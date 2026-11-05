# ADR-22: Bounded Image Viewer

## Decision

- I07 reuses the I04 `ThumbnailSource` contract and requests a 512 px
  MediaStore preview. It does not add original-byte or full-resolution access.
- The viewer takes a deduplicated snapshot of the currently loaded Inbox
  window, so previous/next navigation remains bounded to at most 96 items.
- Fit and 2x detail modes are presentation-only transforms. Each image change
  resets to fit mode and starts a new preview load.
- A preview result is applied only when its source ID still matches the
  current item. Late results from an older item are ignored.
- Loading and unavailable states remain visible. Closing the viewer drops the
  snapshot and Compose preview reference.

## Rationale

I07 depends only on I04. The existing read-only thumbnail source can deliver a
truthful minimum viewer without introducing source-byte access, another cache,
or a new storage permission. A bounded snapshot also prevents paging changes
from invalidating in-view navigation.

## Boundaries

No full-resolution claim, source-byte read, cache persistence, Room schema
change, workspace mutation, plan execution, source cleanup, Journal, or
external write is authorized. Android 11, OEM family A, and OEM family B
remain `OWNER_ACCEPTED_DEFERRED`; release remains blocked.

## Status

Accepted and completed locally on 2026-11-04 for I07 development only.
