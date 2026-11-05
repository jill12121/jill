# ADR-14: Deferred Device Matrix Development Boundary

## Decision

- Android 11, OEM family A, and OEM family B are
  `OWNER_ACCEPTED_DEFERRED`, not PASS or aggregate GO.
- The vivo V2304A/API 36 S01 limited-go candidate, S02 PASS, and S04 PASS
  authorize F05.2 directory selection, persisted URI grants, and app-private
  selection metadata.
- F05.2 must not scan directories, create or modify external files, initialize
  a workspace, read Manifests, or clean source files.
- This decision does not authorize I01/I02/J1/J2/J3.
- COPY_KEEP_SOURCE remains the default. Missing device rows and release policy
  remain release blockers.

## Status

Accepted by owner for development only on 2026-11-03.
