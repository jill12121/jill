# ADR-19: Draft And Selection Plan Boundary

## Decision

- I05 reuses Room schema v1.
- Each batch has at most one active Draft through its stable `activeKey`.
- Draft revisions use optimistic locking and replace actions atomically.
- Revising a Draft marks its prior CREATED/READY Plans STALE.
- A Plan freezes exact file IDs, source fingerprints, scan generation, Draft
  revision, and a sorted detached selection snapshot.
- One Plan may be frozen per Draft revision.
- I05 persists Plans only as CREATED. It cannot make them READY or execute.

## Excluded

Plan confirmation UI, source reads, target creation, Journal, Manifest writes,
copy, cleanup, deletion, WorkManager, and all external mutation.

## Status

Accepted and implemented locally on 2026-11-03.
