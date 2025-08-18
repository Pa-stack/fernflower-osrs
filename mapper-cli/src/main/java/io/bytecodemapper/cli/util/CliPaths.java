// >>> AUTOGEN: BYTECODEMAPPER CLI CliPaths BEGIN
package io.bytecodemapper.cli.util;

import java.nio.file.*;

public final class CliPaths {
    private CliPaths(){}

    /** Resolve a path robustly for Gradle :mapper-cli runs and repo-root runs. */
    public static Path resolveMaybeModuleRelative(String raw) {
        if (raw == null) return null;
        Path rawPath = Paths.get(raw);
        if (rawPath.isAbsolute()) return rawPath.normalize();

        Path cwd = Paths.get("").toAbsolutePath();
        Path repoRoot = cwd;
        if (cwd.getFileName() != null && "mapper-cli".equals(cwd.getFileName().toString())) {
            Path parent = cwd.getParent();
            if (parent != null) repoRoot = parent;
        }
        boolean atRepoRoot = Files.isDirectory(repoRoot.resolve("mapper-cli"));

        // 1) Try CWD
        Path tryCwd = cwd.resolve(raw);
        if (Files.exists(tryCwd)) return tryCwd.normalize();

        // 2) Try repo root
        Path tryRepo = repoRoot.resolve(raw);
        if (Files.exists(tryRepo)) return tryRepo.normalize();

        // 3) For outputs when invoked from repo root: anchor under mapper-cli/ without double-prefixing
        if (atRepoRoot) {
            // If caller already provided a path starting with "mapper-cli/", respect it as module-relative
            if (rawPath.getNameCount() > 0 && "mapper-cli".equals(rawPath.getName(0).toString())) {
                return repoRoot.resolve(rawPath).normalize();
            }
            return repoRoot.resolve("mapper-cli").resolve(rawPath).normalize();
        }

        // 4) Fallback: under CWD
        return tryCwd.normalize();
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI CliPaths END
