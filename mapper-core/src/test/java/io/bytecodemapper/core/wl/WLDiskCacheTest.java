package io.bytecodemapper.core.wl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.Before;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class WLDiskCacheTest implements Opcodes {
    @Before public void setSessionReset(){ System.setProperty("mapper.test.sessionReset","true"); }
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
    java.util.regex.Pattern pSHA = java.util.regex.Pattern.compile("^wl\\.candidates\\.sha256=[0-9a-f]{64}$", java.util.regex.Pattern.MULTILINE);
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

    @Test public void diskCacheHeaderV1AndBackCompat() throws Exception {
        System.setProperty("mapper.cache.disk","true");
        System.setProperty("mapper.debug","true");
        java.nio.file.Path base = java.nio.file.Paths.get("mapper-core","build","tmp","wlcache_v1");
        System.setProperty("mapper.cache.dir", base.toAbsolutePath().normalize().toString());
        try { java.nio.file.Files.walk(base).sorted(java.util.Comparator.reverseOrder()).forEach(p->{ try{ java.nio.file.Files.deleteIfExists(p);}catch(Exception ignored){} }); } catch (Exception ignored) {}

        MethodNode old = loop(); MethodNode new1 = loop(); MethodNode new2 = straight();
        Key o = new Key("oldX"), k1 = new Key("newLoop"), k2 = new Key("newStraight");
        java.util.Map<Object,MethodNode> nodes = new java.util.TreeMap<Object,MethodNode>(new java.util.Comparator<Object>(){ public int compare(Object a,Object b){ return a.toString().compareTo(b.toString()); }});
        nodes.put(o,old); nodes.put(k1,new1); nodes.put(k2,new2);
        java.util.List<Object> newKeys = java.util.Arrays.asList(k1,k2);

        // First run to produce files
        MethodCandidateGenerator.beginSessionCache(4);
        MethodCandidateGenerator.candidatesFor(o, newKeys, MethodCandidateGenerator.DEFAULT_K, nodes);

        // Verify produced file has header 'WLBG'
        java.nio.file.Path dir = java.nio.file.Paths.get(System.getProperty("mapper.cache.dir"));
        java.nio.file.DirectoryStream<java.nio.file.Path> ds = java.nio.file.Files.newDirectoryStream(dir, "*.bin");
        boolean sawHeader = false; for (java.nio.file.Path p : ds) { byte[] b = java.nio.file.Files.readAllBytes(p); if (b.length>=4 && b[0]=='W'&&b[1]=='L'&&b[2]=='B'&&b[3]=='G') { sawHeader = true; break; } }
        ds.close();
        Assert.assertTrue("header v1 must be present", sawHeader);

        // Back-compat v0: write a simple (long,int) only file for some fake key and ensure reader accepts
        String legacyKey = "legacyV0";
        String diskKey = invokeDiskKeyFor(legacyKey);
        java.nio.file.Path legacy = dir.resolve(diskKey + ".bin");
        java.nio.file.Files.createDirectories(legacy.getParent());
        java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(legacy)));
        // one entry
        out.writeLong(123L); out.writeInt(1); out.close();

        // Run with a matching cacheKey usage path to hit the v0 file
        // We'll simulate by directly calling wlBag via candidatesFor using a key that maps to legacyKey
        Key kLegacy = new Key(legacyKey);
        nodes.put(kLegacy, straight()); // any method; we won't inspect contents beyond hit
        java.util.List<Object> newKeys2 = java.util.Arrays.asList(kLegacy);
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream(); java.io.PrintStream cap = new java.io.PrintStream(bout,true,"UTF-8"); java.io.PrintStream real = System.out; System.setOut(cap);
        MethodCandidateGenerator.beginSessionCache(4);
        MethodCandidateGenerator.candidatesFor(kLegacy, newKeys2, MethodCandidateGenerator.DEFAULT_K, nodes);
        System.out.flush(); System.setOut(real);
        String logs = new String(bout.toByteArray(), "UTF-8");
        Assert.assertTrue("v0 hit log", logs.contains("CACHE_HIT:wl key="));
    }

    @Test public void diskCacheCorruptFileSoftDisable() throws Exception {
        System.setProperty("mapper.cache.disk","true");
        System.setProperty("mapper.debug","true");
        java.nio.file.Path base = java.nio.file.Paths.get("mapper-core","build","tmp","wlcache_corrupt");
        System.setProperty("mapper.cache.dir", base.toAbsolutePath().normalize().toString());
        try { java.nio.file.Files.walk(base).sorted(java.util.Comparator.reverseOrder()).forEach(p->{ try{ java.nio.file.Files.deleteIfExists(p);}catch(Exception ignored){} }); } catch (Exception ignored) {}

        // Write a corrupt v1 file for a key
        String key = "corrupt";
        String diskKey = invokeDiskKeyFor(key);
        java.nio.file.Path f = java.nio.file.Paths.get(System.getProperty("mapper.cache.dir")).resolve(diskKey + ".bin");
        java.nio.file.Files.createDirectories(f.getParent());
        byte[] bad = new byte[]{'W','L','B','G',1,0, 0,1,2,3}; // truncated payload
        java.nio.file.Files.write(f, bad);

        // Run a call that would try to read the corrupt file
        MethodNode old = loop(); MethodNode new1 = loop();
        Key o = new Key(key), k1 = new Key("newLoop");
        java.util.Map<Object,MethodNode> nodes = new java.util.TreeMap<Object,MethodNode>(new java.util.Comparator<Object>(){ public int compare(Object a,Object b){ return a.toString().compareTo(b.toString()); }});
        nodes.put(o,old); nodes.put(k1,new1);
        java.util.List<Object> newKeys = java.util.Arrays.asList(k1);
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream(); java.io.PrintStream cap = new java.io.PrintStream(bout,true,"UTF-8"); java.io.PrintStream real = System.out; System.setOut(cap);
        MethodCandidateGenerator.beginSessionCache(2);
        MethodCandidateGenerator.candidatesFor(o, newKeys, MethodCandidateGenerator.DEFAULT_K, nodes);
        System.out.flush(); System.setOut(real);
        String logs = new String(bout.toByteArray(), "UTF-8");
        Assert.assertTrue("soft disable log", logs.matches("(?s).*^\\[CACHE] disabled wl.*$"));
    }

    @Test public void lruFirstSanity() throws Exception {
        System.setProperty("mapper.cache.disk","true");
        System.setProperty("mapper.debug","true");
        java.nio.file.Path base = java.nio.file.Paths.get("mapper-core","build","tmp","wlcache_lru");
        System.setProperty("mapper.cache.dir", base.toAbsolutePath().normalize().toString());
        try { java.nio.file.Files.walk(base).sorted(java.util.Comparator.reverseOrder()).forEach(p->{ try{ java.nio.file.Files.deleteIfExists(p);}catch(Exception ignored){} }); } catch (Exception ignored) {}

        MethodNode old = loop(); MethodNode new1 = loop(); MethodNode new2 = straight();
        Key o = new Key("oldX"), k1 = new Key("newLoop"), k2 = new Key("newStraight");
        java.util.Map<Object,MethodNode> nodes = new java.util.TreeMap<Object,MethodNode>(new java.util.Comparator<Object>(){ public int compare(Object a,Object b){ return a.toString().compareTo(b.toString()); }});
        nodes.put(o,old); nodes.put(k1,new1); nodes.put(k2,new2);
        java.util.List<Object> newKeys = java.util.Arrays.asList(k1,k2);

        // Populate WL_CACHE and disk
        MethodCandidateGenerator.beginSessionCache(4);
        MethodCandidateGenerator.candidatesFor(o, newKeys, MethodCandidateGenerator.DEFAULT_K, nodes);
        // Now call again; expect only HITs
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream(); java.io.PrintStream cap = new java.io.PrintStream(bout,true,"UTF-8"); java.io.PrintStream real = System.out; System.setOut(cap);
        MethodCandidateGenerator.beginSessionCache(4);
        MethodCandidateGenerator.candidatesFor(o, newKeys, MethodCandidateGenerator.DEFAULT_K, nodes);
        System.out.flush(); System.setOut(real);
        String logs = new String(bout.toByteArray(), "UTF-8");
        Assert.assertTrue("must have HIT", logs.contains("CACHE_HIT:wl key="));
    }

    private static String invokeDiskKeyFor(String cacheKey){
        try {
            java.lang.reflect.Method m = io.bytecodemapper.core.wl.MethodCandidateGenerator.class.getDeclaredMethod("diskKeyFor", String.class, String.class);
            m.setAccessible(true);
            return (String)m.invoke(null, cacheKey, "full");
        } catch (Exception e){ throw new RuntimeException(e); }
    }

    private static int countMatches(String s, java.util.regex.Pattern p){ int c=0; java.util.regex.Matcher m = p.matcher(s); while(m.find()) c++; return c; }
}
