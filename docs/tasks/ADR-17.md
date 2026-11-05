# ADR-17: Scanner And Runtime Media Permission Boundary

## Decision

- I01 is split into I01.1 scanner core and I01.2 user-facing permission and
  batch-selection composition.
- I01.1 may declare the platform media read permissions and map granted
  capabilities to `FULL`, `PARTIAL_USER_SELECTED`, or `UNKNOWN`.
- Permission declaration does not authorize an automatic permission prompt.
  I01.2 must use an explicit user action.
- `DefaultScanner` may consume only `SourceDiscoveryGateway` metadata pages.
  It must not use `SourceReader.open`, file bytes, thumbnails, or mutations.
- Pages remain bounded to 500 rows. The scanner rejects oversized pages,
  non-advancing cursors, inconsistent final cursors, and more than 1,000
  pages.
- A provider failure returns no completed snapshot. Partial metadata is not
  presented as a completed scan.
- The existing Camera/Pictures/Downloads setup scopes are not equivalent to
  N01 Download first-level batches. I01.2 must present discovered batches
  separately rather than overloading those controls.
- No Room writes, Plan creation, workspace mutation, Journal, WorkManager,
  source cleanup, or image-byte access is authorized.

## Status

Accepted for development on 2026-11-03.
