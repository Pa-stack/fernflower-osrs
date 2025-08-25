package io.bytecodemapper.core.wl;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class WLDiskCacheTest implements Opcodes {
    private static MethodNode straight(){ MethodNode m=new MethodNode(ACC_PUBLIC|ACC_STATIC, "s","()V",null,null); InsnList il=m.instructions; il.add(new InsnNode(ICONST_1)); il.add(new InsnNode(RETURN)); return m; }
    private static MethodNode loop(){ MethodNode m=new MethodNode(ACC_PUBLIC|ACC_STATIC, "l","()V",null,null); InsnList il=m.instructions; LabelNode L=new LabelNode(); il.add(new InsnNode(ICONST_2)); il.add(L); il.add(new InsnNode(ICONST_1)); il.add(new InsnNode(ISUB)); il.add(new JumpInsnNode(IFGT, L)); il.add(new InsnNode(RETURN)); return m; }
    private static final class Key { private final String id; Key(String id){this.id=id;} public String fingerprintSha256(){ return id; } public String toString(){ return id; } }

    @Test public void diskCacheMissThenHitAndDeterminism(){
        // Enable disk cache and debug; isolate cache dir under build/tmp
        System.setProperty("mapper.cache.disk","true");
        System.setProperty("mapper.debug","true");
        java.nio.file.Path base = java.nio.file.Paths.get("mapper-core","build","tmp","wlcache");
        System.setProperty("mapper.cache.dir", base.toAbsolutePath().normalize().toString());
        try { java.nio.file.Files.walk(base).sorted(java.util.Comparator.reverseOrder()).forEach(p->{ try{ java.nio.file.Files.deleteIfExists(p);}catch(Exception ignored){} }); } catch (Exception ignored) {}

        // Echo metadata
        System.out.println("ALGO_VERSION=DiskWL-v1");
        System.out.println("CANONICAL_CONFIG_SHA256=unknown-for-P1");
        System.out.println("RUNTIME_VERSION=java8+gradle6.9.4");
        System.out.println("COMMIT_OR_ARTIFACT_HASH=local");

        MethodNode old = loop(); MethodNode new1 = loop(); MethodNode new2 = straight();
        Key o = new Key("oldX"), k1 = new Key("newLoop"), k2 = new Key("newStraight");
        java.util.Map<Object,MethodNode> nodes = new java.util.TreeMap<Object,MethodNode>(new java.util.Comparator<Object>(){ public int compare(Object a,Object b){ return a.toString().compareTo(b.toString()); }});
        nodes.put(o,old); nodes.put(k1,new1); nodes.put(k2,new2);
        java.util.List<Object> newKeys = java.util.Arrays.asList(k1,k2);

        // Capture stdout
        java.io.PrintStream realOut = System.out; java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream(); java.io.PrintStream cap; try{ cap = new java.io.PrintStream(bout, true, "UTF-8"); } catch(Exception ex){ cap = new java.io.PrintStream(bout);} System.setOut(cap);

        // Cold run (expect MISS lines)
        MethodCandidateGenerator.beginSessionCache(4);
        MethodCandidateGenerator.candidatesFor(o, newKeys, MethodCandidateGenerator.DEFAULT_K, nodes);
        System.out.flush(); System.setOut(realOut);
        String first; try{ first = new String(bout.toByteArray(), "UTF-8"); } catch(Exception ex){ first = bout.toString(); }
        java.util.regex.Pattern miss = java.util.regex.Pattern.compile("CACHE_MISS:wl key=");
        Assert.assertTrue("MISS lines expected", miss.matcher(first).find());

        // Second run (expect HIT lines and baseline SHA)
        bout.reset(); System.setOut(cap);
        MethodCandidateGenerator.beginSessionCache(4);
        long t0 = System.nanoTime();
        java.util.List<MethodCandidateGenerator.Candidate> cs = MethodCandidateGenerator.candidatesFor(o, newKeys, MethodCandidateGenerator.DEFAULT_K, nodes);
        long warmMs = (System.nanoTime() - t0) / 1_000_000L;
        StringBuilder sb = new StringBuilder(); for(MethodCandidateGenerator.Candidate c: cs){ sb.append(c.newId).append(':').append(String.format(java.util.Locale.ROOT, "%.6f", c.wlScore)).append('\n'); }
        String hex = WLRefinement.sha256Hex(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("wl.candidates.sha256=" + hex);
        System.out.flush(); System.setOut(realOut);
        String second; try{ second = new String(bout.toByteArray(), "UTF-8"); } catch(Exception ex){ second = bout.toString(); }
        java.util.regex.Pattern hit = java.util.regex.Pattern.compile("CACHE_HIT:wl key=");
        Assert.assertTrue("HIT lines expected on second run", hit.matcher(second).find());
        java.util.regex.Pattern pSHA = java.util.regex.Pattern.compile("^wl\\.candidates\\.sha256=21df34dcd20899415f0316d82f87277de559889e444aa6076b1ae06e9e03ab1f$", java.util.regex.Pattern.MULTILINE);
        Assert.assertTrue("baseline candidate SHA missing", pSHA.matcher(second).find());

        // KPI: parse counts from combined logs
        int misses = countMatches(first + second, miss);
        int hits = countMatches(second, hit);
        try{
            java.nio.file.Path kp = java.nio.file.Paths.get("mapper-core","build","tmp","p1_kpi.json");
            java.nio.file.Files.createDirectories(kp.getParent());
            String json = String.format(java.util.Locale.ROOT, "{\"cacheHits\":%d,\"cacheMisses\":%d}", hits, misses);
            java.nio.file.Files.write(kp, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String kpc = new String(java.nio.file.Files.readAllBytes(kp), java.nio.charset.StandardCharsets.UTF_8);
            Assert.assertTrue(kpc, java.util.regex.Pattern.compile("\\\"cacheHits\\\":\\d+").matcher(kpc).find());
            Assert.assertTrue(kpc, java.util.regex.Pattern.compile("\\\"cacheMisses\\\":\\d+").matcher(kpc).find());
        } catch(Exception ex){ Assert.fail("KPI write failed: " + ex.getMessage()); }

        // Smoke-bound on warm time
        Assert.assertTrue("warm run bound", (warmMs/1000.0) <= 30.0);
    }

    private static int countMatches(String s, java.util.regex.Pattern p){ int c=0; java.util.regex.Matcher m = p.matcher(s); while(m.find()) c++; return c; }
}
