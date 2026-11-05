# ADR-15: Read-only Existing Root Validation

## Decision

- F05.3 may read only `.gallery-organizer/root.json` and
  `Albums/*/manifest.json` beneath the selected SAF tree.
- `root.json` schema 1 contains `schemaVersion` and a non-blank `workspaceId`.
- Reads are bounded to 64 KiB per JSON document and 256 album directories.
- Missing, duplicate, truncated, malformed, or unsupported evidence fails
  closed and prevents setup completion.
- IMPORTING manifests are valid adoption evidence but remain recovery work;
  F05.3 reports their count and does not reconcile them.
- No directory creation, file mutation, Journal execution, Room import,
  scanner, cleanup, or recovery action is authorized.

## Status

Accepted by owner for development only on 2026-11-03.
