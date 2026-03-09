# AGENTS.md

## Workspace Rules

- `ref/` is a reference-only folder.
- Files under `ref/` must never be edited, moved, deleted, or overwritten.
- `ref/` may only be read for analysis, migration, or implementation reference.
- All new code and all active project changes must be created outside `ref/`.
- On every project load, check this rule first and treat `ref/` as a read-only legacy/reference source.
