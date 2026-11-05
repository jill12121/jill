# ADR-16: Bounded Read-only Source Discovery

## Decision

- N01 may enumerate image metadata from `MediaStore.Images` only.
- The production boundary is `SourceDiscoveryGateway`; it exposes bounded
  metadata pages and does not expose byte streams, mutation, cleanup, or
  authorization requests.
- Pagination uses `_ID > lastId`, ascending `_ID`, and a hard page limit of
  500. Non-increasing IDs, oversized pages, null cursors, and provider failures
  fail closed.
- Domain policy lists Download first-level directories as batches. Deeper
  directories remain in their first-level batch and retain
  `subgroupRelativePath`.
- Images directly under Download form a virtual loose-image batch that is
  visible but disabled by default.
- The organizer root and user-protected directory prefixes are always
  excluded before aggregation.
- `PARTIAL_USER_SELECTED` metadata may be shown, but whole-batch organization
  remains disabled. Only `FULL` coverage can enable whole-batch behavior after
  a completed scan.
- N01 does not request runtime permission or wire discovery into UI. That
  composition belongs to I01 after a separate review.
- No source bytes, Room writes, Plan creation, external mutation, Journal,
  WorkManager, or cleanup is authorized.

## Status

Accepted for development on 2026-11-03.
