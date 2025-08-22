// >>> AUTOGEN: BYTECODEMAPPER CLI CalibrationSummarizer BEGIN
package io.bytecodemapper.cli.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Summarizes calibration runs under a directory tree into CSV and Markdown.
 * Java 8 only, no external libs. Deterministic traversal and sorting.
 */
public final class CalibrationSummarizer {

    public static void main(String[] args) throws Exception {
        Path base = args != null && args.length > 0 ? Paths.get(args[0]) : Paths.get("out/calibration");
        base = base.toAbsolutePath().normalize();

        if (!Files.exists(base) || !Files.isDirectory(base)) {
            System.err.println("Base directory not found: " + base);
            return;
        }

        List<Path> dirs = listAllDirs(base);
        // Deterministic: sort directory names lexicographically
        Collections.sort(dirs, new Comparator<Path>() {
            public int compare(Path a, Path b) {
                return a.toString().compareTo(b.toString());
            }
        });

        List<Row> rows = new ArrayList<Row>();
        for (Path d : dirs) {
            // Each directory considered a combo container if it has all three files
            Path report = d.resolve("report.json");
            Path timeMs = d.resolve("time_ms.txt");
            Path weights = d.resolve("weights.txt");
            if (!Files.isRegularFile(report) || !Files.isRegularFile(timeMs) || !Files.isRegularFile(weights)) {
                continue; // skip
            }
            try {
                Row r = parseOne(d.getFileName() != null ? d.getFileName().toString() : d.toString(), report, timeMs, weights);
                if (r != null) rows.add(r);
            } catch (Throwable t) {
                // Best-effort: skip bad dirs
            }
        }

        // Sort results: cand_near_p95 asc, then cand_near_median asc, then runtime_ms asc; tie-break by combo_id
        Collections.sort(rows, new Comparator<Row>() {
            public int compare(Row x, Row y) {
                int c;
                c = Long.compare(x.candNearP95, y.candNearP95); if (c != 0) return c;
                c = Long.compare(x.candNearMedian, y.candNearMedian); if (c != 0) return c;
                c = Long.compare(x.runtimeMs, y.runtimeMs); if (c != 0) return c;
                return x.comboId.compareTo(y.comboId);
            }
        });

        // Write outputs in base dir
        Path csv = base.resolve("summary.csv");
        Path md  = base.resolve("summary.md");
        writeCsv(csv, rows);
        writeMd(md, rows);

        System.out.println(csv.toAbsolutePath().toString());
        System.out.println(md.toAbsolutePath().toString());
    }

    private static List<Path> listAllDirs(Path base) throws IOException {
        List<Path> out = new ArrayList<Path>();
        // include base's immediate children and deeper
        Files.walk(base).forEach(p -> {
            try {
                if (Files.isDirectory(p)) out.add(p);
            } catch (Throwable ignore) { }
        });
        return out;
    }

    private static Row parseOne(String comboId, Path report, Path timeMs, Path weights) throws IOException {
        String json = readAll(report);
        long candNearMedian = extractNumber(json, "cand_count_near_median");
        long candNearP95    = extractNumber(json, "cand_count_near_p95");
        long wlRelaxedCands = extractNumber(json, "wl_relaxed_candidates");
        long nearBefore     = extractNumber(json, "near_before_gates");
        long nearAfter      = extractNumber(json, "near_after_gates");
        long flattening     = extractNumber(json, "flattening_detected");

        long runtimeMs = readLongFromFile(timeMs, -1L);

        Map<String, Double> w = parseWeights(weights);
        double wStack = getOrDefault(w, "w_stack", Double.valueOf(-1)).doubleValue();
        double wLits  = getOrDefault(w, "w_lits", Double.valueOf(-1)).doubleValue();
        double tau    = getOrDefault(w, "tau", Double.valueOf(-1)).doubleValue();
        double margin = getOrDefault(w, "margin", Double.valueOf(-1)).doubleValue();

        Row r = new Row();
        r.comboId = comboId;
        r.wStack = wStack;
        r.wLits = wLits;
        r.tau = tau;
        r.margin = margin;
        r.candNearMedian = candNearMedian;
        r.candNearP95 = candNearP95;
        r.wlRelaxedCandidates = wlRelaxedCands;
        r.nearBeforeGates = nearBefore;
        r.nearAfterGates = nearAfter;
        r.flatteningDetected = flattening;
        r.runtimeMs = runtimeMs;
        return r;
    }

    private static String readAll(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static long readLongFromFile(Path p, long defVal) {
        BufferedReader br = null;
        try {
            br = Files.newBufferedReader(p, StandardCharsets.UTF_8);
            String line = br.readLine();
            if (line == null) return defVal;
            line = line.trim();
            if (line.length() == 0) return defVal;
            // read only first token (allow trailing)
            int i = 0; while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
            int j = i; while (j < line.length() && (Character.isDigit(line.charAt(j)) || line.charAt(j) == '-' )) j++;
            String tok = line.substring(i, j);
            return Long.parseLong(tok);
        } catch (Throwable t) {
            return defVal;
        } finally {
            if (br != null) try { br.close(); } catch (IOException ignore) {}
        }
    }

    // Extract integer/double-like number for a key by naive scanning; returns -1 on missing
    private static long extractNumber(String json, String key) {
        if (json == null) return -1;
        String needle = quote(key);
        int idx = json.indexOf(needle);
        if (idx < 0) return -1;
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) return -1;
        int i = colon + 1;
        // skip whitespace
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { i++; continue; }
            break;
        }
        // capture number token: -?digits(.digits)?
        int start = i;
        boolean seenDigit = false;
        boolean dot = false;
        while (i < json.length()) {
            char c = json.charAt(i);
            if ((c >= '0' && c <= '9')) { seenDigit = true; i++; continue; }
            if (c == '-' && i == start) { i++; continue; }
            if (c == '.' && !dot) { dot = true; i++; continue; }
            break;
        }
        if (!seenDigit) return -1;
        String tok = json.substring(start, i);
        try {
            if (tok.indexOf('.') >= 0) {
                double dv = Double.parseDouble(tok);
                return Math.round(dv);
            } else {
                return Long.parseLong(tok);
            }
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String quote(String s) { return "\"" + s + "\""; }

    private static Map<String, Double> parseWeights(Path weights) throws IOException {
        Map<String, Double> m = new HashMap<String, Double>();
        BufferedReader br = null;
        try {
            br = Files.newBufferedReader(weights, StandardCharsets.UTF_8);
            String line = br.readLine();
            if (line == null) return m;
            String[] parts = line.trim().split(",");
            for (String p : parts) {
                String s = p.trim();
                if (s.length() == 0) continue;
                int eq = s.indexOf('=');
                if (eq <= 0 || eq >= s.length()-1) continue;
                String k = s.substring(0, eq).trim();
                String v = s.substring(eq+1).trim();
                try {
                    double dv = Double.parseDouble(v);
                    m.put(k, dv);
                } catch (Throwable ignore) { }
            }
        } finally {
            if (br != null) try { br.close(); } catch (IOException ignore) {}
        }
        return m;
    }

    private static Double getOrDefault(Map<String, Double> m, String k, Double def) {
        Double v = m.get(k);
        return v != null ? v : def;
    }

    private static void writeCsv(Path out, List<Row> rows) throws IOException {
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8);
        try {
            // header
            bw.write("combo_id,w_stack,w_lits,tau,margin,cand_near_median,cand_near_p95,wl_relaxed_candidates,near_before_gates,near_after_gates,flattening_detected,runtime_ms");
            bw.newLine();
            for (Row r : rows) {
                bw.write(escapeCsv(r.comboId)); bw.write(',');
                bw.write(fmtDouble(r.wStack)); bw.write(',');
                bw.write(fmtDouble(r.wLits)); bw.write(',');
                bw.write(fmtDouble(r.tau)); bw.write(',');
                bw.write(fmtDouble(r.margin)); bw.write(',');
                bw.write(Long.toString(r.candNearMedian)); bw.write(',');
                bw.write(Long.toString(r.candNearP95)); bw.write(',');
                bw.write(Long.toString(r.wlRelaxedCandidates)); bw.write(',');
                bw.write(Long.toString(r.nearBeforeGates)); bw.write(',');
                bw.write(Long.toString(r.nearAfterGates)); bw.write(',');
                bw.write(Long.toString(r.flatteningDetected)); bw.write(',');
                bw.write(Long.toString(r.runtimeMs));
                bw.newLine();
            }
        } finally {
            try { bw.close(); } catch (IOException ignore) {}
        }
    }

    private static void writeMd(Path out, List<Row> rows) throws IOException {
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8);
        try {
            bw.write("| combo_id | w_stack | w_lits | tau | margin | cand_near_median | cand_near_p95 | wl_relaxed_candidates | near_before_gates | near_after_gates | flattening_detected | runtime_ms |\n");
            bw.write("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
            for (Row r : rows) {
                bw.write("| "); bw.write(escapeMd(r.comboId)); bw.write(" | ");
                bw.write(fmtDouble(r.wStack)); bw.write(" | ");
                bw.write(fmtDouble(r.wLits)); bw.write(" | ");
                bw.write(fmtDouble(r.tau)); bw.write(" | ");
                bw.write(fmtDouble(r.margin)); bw.write(" | ");
                bw.write(Long.toString(r.candNearMedian)); bw.write(" | ");
                bw.write(Long.toString(r.candNearP95)); bw.write(" | ");
                bw.write(Long.toString(r.wlRelaxedCandidates)); bw.write(" | ");
                bw.write(Long.toString(r.nearBeforeGates)); bw.write(" | ");
                bw.write(Long.toString(r.nearAfterGates)); bw.write(" | ");
                bw.write(Long.toString(r.flatteningDetected)); bw.write(" | ");
                bw.write(Long.toString(r.runtimeMs)); bw.write(" |");
                bw.newLine();
            }
        } finally {
            try { bw.close(); } catch (IOException ignore) {}
        }
    }

    private static String fmtDouble(double v) {
        if (Double.isNaN(v)) return "-1"; // align with missing sentinel
        return String.format(Locale.US, "%.4f", v);
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        boolean needQuotes = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!needQuotes) return s;
        String q = s.replace("\"", "\"\"");
        return "\"" + q + "\"";
    }

    private static String escapeMd(String s) {
        if (s == null) return "";
        // minimal escapes for pipes
        return s.replace("|", "\\|");
    }

    private static final class Row {
        String comboId;
        double wStack, wLits, tau, margin;
        long candNearMedian, candNearP95, wlRelaxedCandidates, nearBeforeGates, nearAfterGates, flatteningDetected, runtimeMs;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI CalibrationSummarizer END
