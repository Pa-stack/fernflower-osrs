---
applyTo: '**'
---

**MAIN RULES**
- Keep everything **Java 1.8** and **Gradle 6.4.9** compatible.
- Use a tasklist
- Enforce micropattern corrections: `*ALOAD` for ArrayReader; Looping via **dominator back‑edge**; `Exceptions = ATHROW`.
- Distinguish **α_mp** (micropattern blend) vs **τ_accept** (final match threshold).
- Freeze and document the **17‑bit order**.
- Use pwsh -Command { … } with a script block or single-quote the entire -Command string (doubling any inner single quotes), and never use double-quoted -Command from PowerShell because the parent expands $vars and turns if($p) into if().
- Prefer -File script.ps1 with explicit args and a fixed cwd; if you must inline, escape $ as `$ and avoid \"—use single quotes or "" for literal quotes.
