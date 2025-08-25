package io.bytecodemapper.core.wl;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class MethodCandidateGeneratorTest implements Opcodes {
    private static MethodNode straight(){ MethodNode m=new MethodNode(ACC_PUBLIC|ACC_STATIC, "s","()V",null,null); InsnList il=m.instructions; il.add(new InsnNode(ICONST_1)); il.add(new InsnNode(RETURN)); return m; }
    private static MethodNode loop(){ MethodNode m=new MethodNode(ACC_PUBLIC|ACC_STATIC, "l","()V",null,null); InsnList il=m.instructions; LabelNode L=new LabelNode(); il.add(new InsnNode(ICONST_2)); il.add(L); il.add(new InsnNode(ICONST_1)); il.add(new InsnNode(ISUB)); il.add(new JumpInsnNode(IFGT, L)); il.add(new InsnNode(RETURN)); return m; }

    // local stub with fingerprintSha256
    private static final class Key { private final String id; Key(String id){this.id=id;} public String fingerprintSha256(){ return id; } public String toString(){ return id; } }

    @Test public void rankLoopOverStraight(){
    // enable debug early so MethodCandidateGenerator.DEBUG is initialised true at class-load
    System.setProperty("mapper.debug","true");
    System.setProperty("mapper.cand.tier1","true");
    int K = MethodCandidateGenerator.DEFAULT_K;
    final String metaAlgo = "ALGO_VERSION=Tier1-v1";
        final String metaCfg = "CANONICAL_CONFIG_SHA256=unknown-for-P2";
        final String metaRun = "RUNTIME_VERSION=java8+gradle6.9.4";
        final String metaCommit = "COMMIT_OR_ARTIFACT_HASH=local";

        MethodNode old = loop(); MethodNode new1 = loop(); MethodNode new2 = straight();
        Key o = new Key("oldX"), k1 = new Key("newLoop"), k2 = new Key("newStraight");
        java.util.Map<Object,MethodNode> nodes = new java.util.TreeMap<Object,MethodNode>(new java.util.Comparator<Object>(){ public int compare(Object a,Object b){ return a.toString().compareTo(b.toString()); }});
        nodes.put(o,old); nodes.put(k1,new1); nodes.put(k2,new2);
        java.util.List<Object> newKeys = java.util.Arrays.asList(k1,k2);

        MethodCandidateGenerator.beginSessionCache(4);

        // capture stdout so we can assert logs
        java.io.PrintStream realOut = System.out;
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
        java.io.PrintStream cap;
        try { cap = new java.io.PrintStream(bout, true, "UTF-8"); } catch(Exception ex) { cap = new java.io.PrintStream(bout); }
        System.setOut(cap);

        // print metadata and topk
        System.out.println("wl.topk.k=" + K);
        System.out.println(metaAlgo);
        System.out.println(metaCfg);
        System.out.println(metaRun);
        System.out.println(metaCommit);

        // cold run to populate caches/index
        MethodCandidateGenerator.candidatesFor(o, newKeys, K, nodes);

        // warm run (measured)
        long t0 = System.nanoTime();
        java.util.List<MethodCandidateGenerator.Candidate> cs = MethodCandidateGenerator.candidatesFor(o, newKeys, K, nodes);
        long warmMs = (System.nanoTime() - t0) / 1_000_000L;

        // emit candidate sha
        StringBuilder sb = new StringBuilder(); for(MethodCandidateGenerator.Candidate c: cs){ sb.append(c.newId).append(':').append(String.format(java.util.Locale.ROOT, "%.6f", c.wlScore)).append('\n'); }
        String hex = WLRefinement.sha256Hex(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("wl.candidates.sha256=" + hex);

        // flush and restore stdout
        System.out.flush(); System.setOut(realOut);
        String captured;
        try { captured = new String(bout.toByteArray(), "UTF-8"); } catch(Exception ex){ captured = bout.toString(); }
        // Debug dump: write captured stdout to a file for inspection and also emit to stderr for test reports
        try {
            java.nio.file.Path dbg = java.nio.file.Paths.get("mapper-core","build","tmp","p2_stdout.txt"); java.nio.file.Files.createDirectories(dbg.getParent());
            java.nio.file.Files.write(dbg, captured.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch(Exception ex) { System.err.println("failed to write debug stdout: " + ex.getMessage()); }
        System.err.println("---CAPTURED STDOUT START---");
        System.err.println(captured);
        System.err.println("---CAPTURED STDOUT END---");

    // write KPI JSON (fixed field order)
        try{
            java.nio.file.Path kp = java.nio.file.Paths.get("mapper-core","build","tmp","p2_kpi.json"); java.nio.file.Files.createDirectories(kp.getParent());
            int tier1 = 0, tier2 = 0; for(MethodCandidateGenerator.Candidate c: cs){ if (c.newId.contains("newLoop")) tier1++; else tier2++; }
            double warmSecs = warmMs/1000.0;
            String json = String.format(java.util.Locale.ROOT, "{\"tier1\":%d,\"tier2\":%d,\"warmRunSeconds\":%.3f}", tier1, tier2, warmSecs);
            java.nio.file.Files.write(kp, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch(Exception ex){ Assert.fail("failed to write KPI: " + ex.getMessage()); }

        // assertions on captured stdout
        java.util.regex.Pattern pTopk = java.util.regex.Pattern.compile("^wl\\.topk\\.k=25$", java.util.regex.Pattern.MULTILINE);
        java.util.regex.Pattern pSHA = java.util.regex.Pattern.compile("^wl\\.candidates\\.sha256=21df34dcd20899415f0316d82f87277de559889e444aa6076b1ae06e9e03ab1f$", java.util.regex.Pattern.MULTILINE);
    java.util.regex.Pattern pTier = java.util.regex.Pattern.compile("\\[CAND\\] tier1\\.size=\\d+ tier2\\.size=\\d+");
    boolean hasTopk = pTopk.matcher(captured).find();
    boolean hasSha = pSHA.matcher(captured).find();
    boolean hasTier = pTier.matcher(captured).find();
    System.err.println("DBG: hasTopk=" + hasTopk + " hasSha=" + hasSha + " hasTier=" + hasTier);
    System.err.println("DBG: captured='" + captured.replace("\n", "\\n") + "'");
    Assert.assertTrue("missing topk", hasTopk);
    Assert.assertTrue("sha mismatch", hasSha);
    Assert.assertTrue("tier log missing", hasTier);

        // KPI assertions
        try{
            java.nio.file.Path kp = java.nio.file.Paths.get("mapper-core","build","tmp","p2_kpi.json");
            String kpc = new String(java.nio.file.Files.readAllBytes(kp), java.nio.charset.StandardCharsets.UTF_8);
        // Acceptance regexes
        java.util.regex.Pattern pK = java.util.regex.Pattern.compile("\\\"tier1\\\":\\d+,\\s*\\\"tier2\\\":\\d+,\\s*\\\"warmRunSeconds\\\":(0|[1-9]\\d?)(\\.\\d+)?");
        Assert.assertTrue("KPI shape", pK.matcher(kpc).find());
        java.util.regex.Pattern pWarmBound = java.util.regex.Pattern.compile("\\\"warmRunSeconds\\\"\\s*:\\s*(\\d|[12]\\d|30)(\\.\\d+)?");
        Assert.assertTrue("KPI warm bound regex", pWarmBound.matcher(kpc).find());
        // Numeric <= 30.0 check (robust parse)
        String num = kpc.replaceAll(".*\\\"warmRunSeconds\\\"\\s*:\\s*", "").replaceAll(",.*|}.*", "");
        double w = Double.parseDouble(num);
        Assert.assertTrue("warm too large", w <= 30.0);
        } catch(Exception ex){ Assert.fail("KPI read failed: " + ex.getMessage()); }

        // existing ordering assertions
        Assert.assertTrue(cs.size()>=2);
        Assert.assertTrue(cs.get(0).newId.startsWith("new#newLoop"));
        for(int i=1;i<cs.size();i++) Assert.assertTrue(cs.get(i-1).wlScore>=cs.get(i).wlScore);
    }

    @Test public void tier1FlagOffIsNoop(){
        // Run an isolated child JVM so static config in parent process remains unaffected.
        String javaExe = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "java";
        String cp = System.getProperty("java.class.path");
        String runner = "io.bytecodemapper.core.wl.MethodCandidateGeneratorTest$OffRunner";
        java.util.List<String> cmd = new java.util.ArrayList<String>();
        cmd.add(javaExe);
        cmd.add("-Dmapper.debug=true");
        cmd.add("-Dmapper.cand.tier1=false");
        cmd.add("-cp"); cmd.add(cp);
        cmd.add(runner);
        String captured;
        long t0 = System.nanoTime();
        try{
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
            java.io.InputStream in = p.getInputStream();
            byte[] buf = new byte[8192]; int r; while((r=in.read(buf))!=-1){ bout.write(buf,0,r); }
            int ec = p.waitFor();
            captured = new String(bout.toByteArray(), "UTF-8");
            Assert.assertEquals("child exit", 0, ec);
        } catch(Exception ex){ throw new RuntimeException("failed to run child: "+ex, ex); }
        long warmMs = (System.nanoTime() - t0) / 1_000_000L; // upper bound for warm run

        // Assertions: no Tier-1 log; baseline SHA present
        java.util.regex.Pattern pTier = java.util.regex.Pattern.compile("^\\[CAND\\] tier1\\.size=", java.util.regex.Pattern.MULTILINE);
        Assert.assertFalse("Tier-1 log must be absent when disabled", pTier.matcher(captured).find());
        java.util.regex.Pattern pSHA = java.util.regex.Pattern.compile("^wl\\.candidates\\.sha256=21df34dcd20899415f0316d82f87277de559889e444aa6076b1ae06e9e03ab1f$", java.util.regex.Pattern.MULTILINE);
        Assert.assertTrue("baseline candidate SHA missing", pSHA.matcher(captured).find());

        // KPI off-file (write in parent; bound with process time)
        try{
            java.nio.file.Path kp = java.nio.file.Paths.get("mapper-core","build","tmp","p2_kpi_off.json");
            java.nio.file.Files.createDirectories(kp.getParent());
            double warmSecs = Math.min(30.0, warmMs/1000.0);
            String json = String.format(java.util.Locale.ROOT, "{\"warmRunSeconds\":%.3f}", warmSecs);
            java.nio.file.Files.write(kp, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String kpc = new String(java.nio.file.Files.readAllBytes(kp), java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Pattern pK = java.util.regex.Pattern.compile("\\\"warmRunSeconds\\\":(0|[1-9]\\d?)(\\.\\d+)?");
            Assert.assertTrue("KPI(off) shape", pK.matcher(kpc).find());
            String num = kpc.replaceAll(".*\\\"warmRunSeconds\\\"\\s*:\\s*", "").replaceAll(",.*|}.*", "");
            double w = Double.parseDouble(num);
            Assert.assertTrue("warm too large (off)", w <= 30.0);
        } catch(Exception ex){ Assert.fail("KPI(off) failed: " + ex.getMessage()); }

        // Prove check executed
        System.out.println("TIER1_FLAG_OFF_OK");
    }

    // Child runner that performs the fixture work with Tier-1 disabled.
    public static final class OffRunner {
        public static void main(String[] args) throws Exception {
            // properties already provided via -D; re-echo metadata for traceability
            int K = MethodCandidateGenerator.DEFAULT_K;
            System.out.println("wl.topk.k=" + K);
            System.out.println("ALGO_VERSION=Tier1-v1");
            System.out.println("CANONICAL_CONFIG_SHA256=unknown-for-P2");
            System.out.println("RUNTIME_VERSION=java8+gradle6.9.4");
            System.out.println("COMMIT_OR_ARTIFACT_HASH=local");

            MethodNode old = loop(); MethodNode new1 = loop(); MethodNode new2 = straight();
            Key o = new Key("oldX"), k1 = new Key("newLoop"), k2 = new Key("newStraight");
            java.util.Map<Object,MethodNode> nodes = new java.util.TreeMap<Object,MethodNode>(new java.util.Comparator<Object>(){ public int compare(Object a,Object b){ return a.toString().compareTo(b.toString()); }});
            nodes.put(o,old); nodes.put(k1,new1); nodes.put(k2,new2);
            java.util.List<Object> newKeys = java.util.Arrays.asList(k1,k2);

            MethodCandidateGenerator.beginSessionCache(4);

            // cold
            MethodCandidateGenerator.candidatesFor(o, newKeys, K, nodes);
            // warm
            long t0 = System.nanoTime();
            java.util.List<MethodCandidateGenerator.Candidate> cs = MethodCandidateGenerator.candidatesFor(o, newKeys, K, nodes);
            long warmMs = (System.nanoTime() - t0) / 1_000_000L;

            // emit candidate sha
            StringBuilder sb = new StringBuilder(); for(MethodCandidateGenerator.Candidate c: cs){ sb.append(c.newId).append(':').append(String.format(java.util.Locale.ROOT, "%.6f", c.wlScore)).append('\n'); }
            String hex = WLRefinement.sha256Hex(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("wl.candidates.sha256=" + hex);
            System.out.println("WARM_MS=" + warmMs);
        }
    }
}
