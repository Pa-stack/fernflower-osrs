// >>> AUTOGEN: BYTECODEMAPPER CLI Cache MethodFeatureCache BEGIN
package io.bytecodemapper.cli.cache;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public final class MethodFeatureCache implements Closeable {
    private final Path cacheFile;
    private final LinkedHashMap<String, MethodFeatureCacheEntry> map;
    private static final String MAGIC = "BMAP:MFC:1\n"; // simple header to version cache format

    private MethodFeatureCache(Path cacheFile, LinkedHashMap<String, MethodFeatureCacheEntry> map) {
        this.cacheFile = cacheFile;
        this.map = map;
    }

    public static MethodFeatureCache open(Path cacheDir, String jarKey) throws IOException {
        if (cacheDir == null) cacheDir = new File("mapper-cli/build/cache").toPath();
        Files.createDirectories(cacheDir);
        Path file = cacheDir.resolve(jarKey + ".methods.ser");
        LinkedHashMap<String, MethodFeatureCacheEntry> m = new LinkedHashMap<String, MethodFeatureCacheEntry>();
        if (Files.exists(file)) {
            // Guard against legacy or corrupt caches: if too large, skip reading to avoid OOM in tests/CI
            try {
                long size = Files.size(file);
                // ~64 MiB cap: current test-sized caches are << this; weekly datasets may exceed
                final long MAX_BYTES = 64L * 1024L * 1024L;
                if (size > MAX_BYTES) {
                    // Treat as empty; a fresh run will repopulate a small subset deterministically
                    return new MethodFeatureCache(file, m);
                }
            } catch (Throwable ignored) {
                // If size probe fails, continue with cautious read below
            }
            java.io.InputStream is = Files.newInputStream(file);
            try {
                // Read and verify magic header; if absent/mismatch, ignore legacy file content
                byte[] head = new byte[MAGIC.length()];
                int r = is.read(head);
                boolean ok = r == MAGIC.length() && new String(head, java.nio.charset.StandardCharsets.UTF_8).equals(MAGIC);
                if (ok) {
                    ObjectInputStream ois = new ObjectInputStream(is);
                    try {
                        Object obj = ois.readObject();
                        if (obj instanceof LinkedHashMap) {
                            @SuppressWarnings("unchecked")
                            LinkedHashMap<String, MethodFeatureCacheEntry> mm = (LinkedHashMap<String, MethodFeatureCacheEntry>) obj;
                            m.putAll(mm);
                        }
                    } catch (ClassNotFoundException ignored) {
                    } finally {
                        ois.close();
                    }
                } else {
                    // legacy/no-header: skip reading to avoid potential OOM from corrupted content
                }
            } finally {
                try { is.close(); } catch (IOException ignored) {}
            }
        }
        return new MethodFeatureCache(file, m);
    }

    public MethodFeatureCacheEntry get(String key) {
        return map.get(key);
    }

    public void put(String key, MethodFeatureCacheEntry val) {
        map.put(key, val);
    }

    public void flush() throws IOException {
        // Deterministic write: LinkedHashMap iteration order = insertion order; entries were added deterministically.
        java.io.OutputStream os = Files.newOutputStream(cacheFile);
        try {
            // Write header then object stream
            os.write(MAGIC.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ObjectOutputStream oos = new ObjectOutputStream(os);
            try {
                oos.writeObject(map);
            } finally {
                oos.close();
            }
        } finally {
            try { os.close(); } catch (IOException ignored) {}
        }
    }

    @Override public void close() throws IOException { flush(); }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI Cache MethodFeatureCache END
