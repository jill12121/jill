# ADR-20: Provisional I04 Thumbnail Boundary

## Decision

- Android 11, OEM family A, and OEM family B S05 rows are
  `OWNER_ACCEPTED_DEFERRED`.
- The missing rows are not PASS evidence and do not produce aggregate S05 GO.
- I04 development may proceed with the measured target row and provisional
  budgets. Release still requires all four distinct device rows to pass.
- Production thumbnails use `ContentResolver.loadThumbnail` at 256 px.
- The memory cache is capped at the smaller of 5% of the runtime heap and
  64 MiB. Failed loads are not cached.
- Source metadata uses bounded keyset pages. UI and presentation code cannot
  access Room or Android media APIs directly.

## Compatibility

Android 11 and both OEM families remain supported development targets. Their
validation is deferred, not removed. Provider, permission, missing-media, and
decode failures must return an explicit placeholder result without mutation.

## Excluded

Disk thumbnail caches, network access, source writes, full-resolution decode,
workspace mutation, and release approval.

## Status

Accepted by owner on 2026-11-03 for I04 development only.
