// >>> AUTOGEN: BYTECODEMAPPER CLI Cache CacheMeta BEGIN
package io.bytecodemapper.cli.cache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Per-jar cache metadata to record normalizer version and options fingerprint.
 */
public final class CacheMeta {
    private CacheMeta() {}

    public static void write(Path cacheDir, String jarKey, String normalizerVersion, String optionsFp) throws IOException {
        if (cacheDir == null || jarKey == null) return;
        Files.createDirectories(cacheDir);
        Path p = cacheDir.resolve(jarKey + ".meta.properties");
        Properties props = new Properties();
        props.setProperty("normalizerVersion", normalizerVersion != null ? normalizerVersion : "0");
        props.setProperty("optionsFingerprint", optionsFp != null ? optionsFp : "");
        try (OutputStream out = Files.newOutputStream(p)) {
            props.store(out, "BytecodeMapper cache metadata");
        }
    }

    public static Properties read(Path cacheDir, String jarKey) throws IOException {
        Properties props = new Properties();
        if (cacheDir == null || jarKey == null) return props;
        Path p = cacheDir.resolve(jarKey + ".meta.properties");
        if (Files.exists(p)) {
            try (InputStream in = Files.newInputStream(p)) {
                props.load(in);
            }
        }
        return props;
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI Cache CacheMeta END
