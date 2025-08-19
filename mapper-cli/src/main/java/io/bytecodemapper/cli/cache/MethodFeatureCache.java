// >>> AUTOGEN: BYTECODEMAPPER CLI Cache MethodFeatureCache BEGIN
package io.bytecodemapper.cli.cache;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public final class MethodFeatureCache implements Closeable {
    private final Path cacheFile;
    private final LinkedHashMap<String, MethodFeatureCacheEntry> map;

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
            ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(file));
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
        ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(cacheFile));
        try {
            oos.writeObject(map);
        } finally {
            oos.close();
        }
    }

    @Override public void close() throws IOException { flush(); }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI Cache MethodFeatureCache END
