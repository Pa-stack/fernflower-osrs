package io.bytecodemapper.core.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Tiny file I/O helper for cache read/write (Java 8-safe). */
public final class FileCacheIO {
    private FileCacheIO() {}

    public static Path ensureDir(Path dir) throws IOException {
        Path p = dir.toAbsolutePath().normalize();
        Files.createDirectories(p);
        return p;
    }

    public static DataInputStream newDataInputStream(Path file) throws IOException {
        return new DataInputStream(new BufferedInputStream(Files.newInputStream(file)));
    }

    public static DataOutputStream newDataOutputStream(Path file) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)));
    }

    public static Path tempSibling(Path target) {
        return target.getParent().resolve(target.getFileName().toString() + ".tmp");
    }

    public static void atomicReplace(Path tmp, Path target) throws IOException {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
