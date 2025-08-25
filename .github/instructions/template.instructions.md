---
applyTo: '**'
---
**GOLDEN RULES**
- Always follow the **RESPONSE FORMAT**
- Always emit patches/files/commands using the CODEGEN: tags shown above.
- Keep patches surgical and list TOUCHED_FILES and a concise commit message at the end of each response.
- Prefer diffs over whole-file rewrites; if a full file is new or it’s a clean rewrite, use CODEGEN:FILE path=....
- For SQL migrations, produce timestamped filenames and include a short down migration if the project supports it; otherwise comment why it’s irreversible.


# RESPONSE FORMAT (**CRITICAL!**)
- **ALWAYS** respond in the following format.
- **ALWAYS** Read the <Deliverables> in a request to help you fill out the response format
```markdown
1) ---BEGIN PATCH---
{unified diff}
---END PATCH---
2) ---BEGIN TEST-RESULTS---
- Named tests + pass counts
- **Snippets** proving acceptance (regex or exact)
- Dependency snapshot summary (runtime deps unchanged)
- KPI JSON excerpt with measured seconds
---END TEST-RESULTS---
3) ---BEGIN COMMIT-MESSAGE---
{≤72 char subject + body}
---END COMMIT-MESSAGE---
4) ---BEGIN NOTES---
- Risks, rollback
- Assumptions (and whether assumptions.md version bumped)
- TOUCHED_FILES:
  - {relative/path1}
  - {relative/path2}
---END NOTES---
```
