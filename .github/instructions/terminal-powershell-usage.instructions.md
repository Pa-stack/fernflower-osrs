---
applyTo: '**'
---

**(INTEGRATED) TERMINAL USAGE**
1. When launching pwsh from inside PowerShell and passing inline code, then use -Command { … } or single-quote the entire command (doubling inner single quotes) and never use a double-quoted -Command.
2. When your inline code contains variables or quotes, then escape $ as `$ and use "" for a literal quote instead of \"—or avoid all this by using a script block.
3. When the logic isn’t a tiny one-liner, then use -File script.ps1 with explicit params and set a fixed working directory in VS Code tasks (e.g., "options": { "cwd": "${workspaceFolder}" }).
4. The integrated terminal uses powershell 7
