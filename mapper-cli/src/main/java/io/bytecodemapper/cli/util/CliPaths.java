// >>> AUTOGEN: BYTECODEMAPPER CLI CliPaths BEGIN
package io.bytecodemapper.cli.util;

import java.nio.file.*;

public final class CliPaths {
    private CliPaths(){}

    /**
     * Resolve an INPUT path:
     * 1) as given (CWD-relative),
     * 2) repo-root relative,
     * 3) otherwise return the absolute of the original (no anchoring under mapper-cli).
     * NEVER creates directories or re-anchors under module folders.
     */
    public static Path resolveInput(String raw) {
        if (raw == null) return null;
        Path rp = Paths.get(raw);
        if (rp.isAbsolute()) return rp.normalize();

        Path cwd = Paths.get("").toAbsolutePath();

        Path tryCwd = cwd.resolve(raw);
        if (Files.exists(tryCwd)) return tryCwd.normalize();

        Path repoRoot = findRepoRoot(cwd);
        Path tryRepo = repoRoot.resolve(raw);
        if (Files.exists(tryRepo)) return tryRepo.normalize();

        // Fall back to absolute of original; callers should check existence.
        return tryCwd.normalize();
    }

    /**
     * Resolve an OUTPUT path:
     * 1) If absolute → return as-is.
     * 2) If at repo root (has mapper-cli), anchor under repoRoot/mapper-cli/<raw> unless already starts with mapper-cli/.
     * 3) Else CWD-relative.
     * Caller may create parent dirs.
     */
    public static Path resolveOutput(String raw) {
        if (raw == null) return null;
        Path rp = Paths.get(raw);
        if (rp.isAbsolute()) return rp.normalize();

        Path cwd = Paths.get("").toAbsolutePath();
        Path repoRoot = findRepoRoot(cwd);
        boolean atRepoRoot = Files.isDirectory(repoRoot.resolve("mapper-cli"));

        if (atRepoRoot) {
            if (rp.getNameCount() > 0 && "mapper-cli".equals(rp.getName(0).toString())) {
                return repoRoot.resolve(rp).normalize();
            }
            return repoRoot.resolve("mapper-cli").resolve(rp).normalize();
        }
        return cwd.resolve(rp).normalize();
    }

    /** Detect repo root as parent of mapper-cli when running inside modules; else use CWD. */
    private static Path findRepoRoot(Path cwd) {
        if (cwd.getFileName() != null && "mapper-cli".equals(cwd.getFileName().toString())) {
            Path parent = cwd.getParent();
            if (parent != null) return parent;
        }
        return cwd;
    }

    /**
     * @deprecated Prefer resolveInput/resolveOutput for clarity.
     * Retained for idempotency with earlier prompts.
     */
    @Deprecated
    public static Path resolveMaybeModuleRelative(String raw) {
        // Heuristic: assume outputs by default (old behavior).
        return resolveOutput(raw);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI CliPaths END
