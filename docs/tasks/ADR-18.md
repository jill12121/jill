# ADR-18: Source Metadata Persistence Boundary

## Decision

- I03 reuses Room schema v1. No entity or migration change is required.
- A completed FULL snapshot transaction replaces Download `BatchMember` rows.
- A PARTIAL snapshot only merges visible rows and never sweeps hidden members.
- `FileFact` stores MediaStore metadata only: locator identity, path, size,
  normalized modification time, MIME, and scan generation.
- `SourceDecision` applies only to the exact
  `kind:root:path:size:mtimeNormalized` source key.
- An item is imported only when `ImportRecord.sourceFileId` and
  `sourceLocator` match the current item and state is a durable committed or
  cleanup outcome.
- Scanner composition may persist metadata after a completed scan. Scanner
  itself still cannot write decisions, read source bytes, or mutate files.

## Excluded

Room schema changes, selection Drafts, Plans, thumbnails, source-byte reads,
workspace writes, Journal, cleanup, deletion, and background execution.

## Status

Accepted and implemented locally on 2026-11-03.
