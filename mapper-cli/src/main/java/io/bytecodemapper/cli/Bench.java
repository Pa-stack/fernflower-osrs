// >>> AUTOGEN: BYTECODEMAPPER CLI Bench BEGIN
package io.bytecodemapper.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Bench {

	static final class Pair {
		final String id; final String oldJar; final String newJar; final String note;
		Pair(String id, String oldJar, String newJar, String note) { this.id=id; this.oldJar=oldJar; this.newJar=newJar; this.note=note; }
	}

	static final class Metrics {
		int pairs;
		int totalAccepted;
		int totalAbstained;
		long wallMs;
		List<Map<String,Object>> byPair = new ArrayList<Map<String,Object>>();
	}

	private Bench(){}

	public static int run(String[] args) {
		try {
			File manifest = null, outDir = new File("build/bench/out"), metricsOut = new File("build/bench/metrics.json");
			boolean deterministic = true;

			for (int i=0;i<args.length;i++) {
				String a = args[i];
				if ("--manifest".equals(a) && i+1<args.length) manifest = new File(args[++i]);
				else if ("--outDir".equals(a) && i+1<args.length) outDir = new File(args[++i]);
				else if ("--metricsOut".equals(a) && i+1<args.length) metricsOut = new File(args[++i]);
				else if ("--deterministic".equals(a)) deterministic = true;
			}
			// Normalize paths relative to repo root/module
			if (manifest != null) {
				java.nio.file.Path mp = io.bytecodemapper.cli.util.CliPaths.resolveInput(manifest.getPath());
				manifest = mp.toFile();
			}
			if (outDir != null) {
				java.nio.file.Path op = io.bytecodemapper.cli.util.CliPaths.resolveOutput(outDir.getPath());
				outDir = op.toFile();
			}
			if (metricsOut != null) {
				java.nio.file.Path mp2 = io.bytecodemapper.cli.util.CliPaths.resolveOutput(metricsOut.getPath());
				metricsOut = mp2.toFile();
			}

			if (manifest == null || !manifest.isFile()) {
				System.err.println("bench requires --manifest <pairs.json>");
				return 2;
			}
			List<Pair> pairs = readManifest(manifest);
			Collections.sort(pairs, new Comparator<Pair>() {
				public int compare(Pair a, Pair b) {
					int c = nz(a.id).compareTo(nz(b.id)); if (c!=0) return c;
					c = a.oldJar.compareTo(b.oldJar); if (c!=0) return c;
					return a.newJar.compareTo(b.newJar);
				}
				private String nz(String s){ return s==null?"":s; }
			});

			outDir.mkdirs();
			long t0 = System.currentTimeMillis();
			Metrics agg = new Metrics();
			for (Pair p : pairs) {
				String outName = (p.id!=null && !p.id.isEmpty() ? p.id : base(p.oldJar)+"_"+base(p.newJar)) + ".tiny";
				File outMap = new File(outDir, outName);
				// Call existing map command; capture stats via returns/logs
				MapOldNew.Result res = MapOldNew.runProgrammatic(p.oldJar, p.newJar, outMap.getAbsolutePath(), deterministic, new String[]{"--maxMethods","100"});
				Map<String,Object> one = new LinkedHashMap<String,Object>();
				one.put("id", p.id);
				one.put("old", p.oldJar);
				one.put("new", p.newJar);
				one.put("accepted", Integer.valueOf(res.acceptedCount));
				one.put("abstained", Integer.valueOf(res.abstainedCount));
				one.put("out", outMap.getName());
				one.put("note", p.note);
				agg.byPair.add(one);
				agg.totalAccepted += res.acceptedCount;
				agg.totalAbstained += res.abstainedCount;
			}
			agg.pairs = pairs.size();
			agg.wallMs = System.currentTimeMillis() - t0; // measured but not emitted to keep determinism

			// Optional console summary
			System.out.println("[bench] pairs=" + agg.pairs + " totalAccepted=" + agg.totalAccepted + " totalAbstained=" + agg.totalAbstained);

			// Write metrics JSON deterministically
			if (metricsOut.getParentFile()!=null) metricsOut.getParentFile().mkdirs();
			writeMetricsJson(metricsOut, agg);
			System.out.println("[bench] Wrote metrics to " + metricsOut.getAbsolutePath());
			return 0;
		} catch (Exception e) {
			e.printStackTrace();
			return 1;
		}
	}

	private static String base(String path) {
		int s = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		int e = path.lastIndexOf('.');
		if (e <= s) e = path.length();
		return path.substring(s+1, e);
	}

	// Minimal JSON parse: expect {"pairs":[{id?,old,new,note?},...]}
	private static List<Pair> readManifest(File f) throws IOException {
		String s = readAll(f);
		// zero-dep, naive parse for simple schema
		List<Pair> out = new ArrayList<Pair>();
		int arr = s.indexOf("\"pairs\"");
		if (arr < 0) throw new IOException("manifest missing 'pairs' key");
		int lb = s.indexOf('[', arr);
		int rb = s.indexOf(']', lb);
		if (lb<0 || rb<0) throw new IOException("manifest 'pairs' not an array");
		String body = s.substring(lb+1, rb);
		// split by "},{" boundaries (naive but stable for our simple test schema)
		String[] objs = body.split("\\}\\s*,\\s*\\{");
		for (String raw : objs) {
			String obj = raw;
			if (!obj.startsWith("{")) obj = "{"+obj;
			if (!obj.endsWith("}")) obj = obj + "}";
			String id = findString(obj, "id");
			String old = must(findString(obj, "old"), "old");
			String nw  = must(findString(obj, "new"), "new");
			String note= findString(obj, "note");
			out.add(new Pair(id, old, nw, note));
		}
		return out;
	}

	private static String must(String v, String key) throws IOException { if (v==null) throw new IOException("manifest pair missing '"+key+"'"); return v; }

	private static String findString(String json, String key) {
		int k = json.indexOf("\""+key+"\"");
		if (k<0) return null;
		int c = json.indexOf(':', k);
		if (c<0) return null;
		int q1 = json.indexOf('"', c+1);
		if (q1<0) return null;
		int q2 = json.indexOf('"', q1+1);
		if (q2<0) return null;
		return json.substring(q1+1, q2);
	}

	private static String readAll(File f) throws IOException {
		byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
		return new String(b, StandardCharsets.UTF_8);
	}

	private static void writeMetricsJson(File f, Metrics m) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"pairs\": ").append(m.pairs).append(",\n");
		sb.append("  \"totalAccepted\": ").append(m.totalAccepted).append(",\n");
	sb.append("  \"totalAbstained\": ").append(m.totalAbstained).append(",\n");
		sb.append("  \"items\": [\n");
		for (int i=0;i<m.byPair.size();i++) {
			Map<String,Object> it = m.byPair.get(i);
			sb.append("    {");
			// deterministic key order
			sb.append("\"id\":\"").append(esc((String)it.get("id"))).append("\",");
			sb.append("\"old\":\"").append(esc((String)it.get("old"))).append("\",");
			sb.append("\"new\":\"").append(esc((String)it.get("new"))).append("\",");
			sb.append("\"accepted\":").append(it.get("accepted")).append(",");
			sb.append("\"abstained\":").append(it.get("abstained")).append(",");
			sb.append("\"out\":\"").append(esc((String)it.get("out"))).append("\",");
			sb.append("\"note\":\"").append(esc((String)it.get("note"))).append("\"");
			sb.append("}");
			if (i+1<m.byPair.size()) sb.append(",");
			sb.append("\n");
		}
		sb.append("  ]\n");
		sb.append("}\n");
		if (f.getParentFile()!=null) f.getParentFile().mkdirs();
		try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
			w.write(sb.toString());
		}
	}

	private static String esc(String s) { return s==null?"":s.replace("\\","\\\\").replace("\"","\\\""); }
}
// >>> AUTOGEN: BYTECODEMAPPER CLI Bench END
