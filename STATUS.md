# Status

## Current Task

- ID: S01/S02/S04
- Sub-step: Offline physical-device evidence collection
- State: READY_FOR_OFFLINE_DEVICE_VALIDATION; DEVICE_RESULTS_PENDING

## Completed

- F01: repository, toolchain, baseline specification, CI, and policy guards established.
- F02 series: module boundaries, Hilt composition root, typed Ports, architecture
  guards, delivery hardening, and release isolation completed.
- F03 series: pure domain contracts, typed Plan payloads, state machines,
  permission matrices, and failure resolution completed.
- F04 series: Room v1 metadata, constraints, test-only Fakes/FaultPlan, and
  minimum integration harness completed; no production storage execution.
- S01.1: hardened SAF probe committed as `94866ec`; Remote Verify `39919115736` passed.
- S02/S04 scaffold: combined probe APK committed as `5ffcac4`; Remote Verify
  `39932002281` passed; device results remain PENDING.
- S01.2: probe-owned SAF mutation isolation committed as `e4b5710`; Remote
  Verify `40444298467` passed; independent review remains required.
- S02.1: runtime permission, bounded pagination evidence, and controlled cleanup
  authorization committed as `dec1951`; Remote Verify `40446516962` passed.
- S04.1: persisted two-stage restart observation committed as `45cecff`; Remote
  Verify `40447648083` passed.
- S01.3: exported result JSON now carries manufacturer, security patch, and a
  SHA-256 fingerprint hash instead of the raw build fingerprint; committed as
  `5dd458e`; Remote Verify `40450678249` passed.
- S01.4: ownership validation rejects malformed marker fields and mismatched or
  replaced samples, while the cleanup ledger tracks every created document;
  committed as `c44e511`; Remote Verify `122067814285` passed. All 15 S01 unit
  tests are green.
- S02.2: strict monotonic bounded pagination, honored query arguments, cleanup
  tri-state evidence, and explicit fixture ownership were committed as
  `941efd4`; Remote Verify `40469408370` passed.
- S02.3: pending cleanup authorization now survives Activity recreation, and
  exported evidence binds schema/probe/run/app/device provenance while exposing
  only a SHA-256 build fingerprint hash; committed as `14e6e9e`; Remote Verify
  `40471142170` passed. At that baseline, all 6 S02 unit tests were green.
- S01/S02 lifecycle hardening: cleanup attempts all tracked S01 entries,
  adoption delete candidates are failure-safe, S02 completion stays in the
  retained ViewModel, and pagination evidence defaults fail closed; commit
  `2ed4e7f` passed Remote Verify `40482946612`.
- S02.4: independent helper APK `com.galleryorganizer.spikes.s02.fixturehelper`
  creates the random-token fixture under a distinct UID. S02 validates owner,
  path, MIME, name, token, and bounded payload before delete authorization;
  commit `a9ef42a` passed Remote Verify `40623103758`. All 8 S02 unit tests
  are green.
- S04.2: durable `PREPARING` -> `READY` -> `OBSERVED` phases make observation
  and cleanup crash-resumable; committed as `31f24dd`; Remote Verify
  `40472524359` passed.
- S04.3: overwrite behavior is classified by exact names and parsed
  generations, and every stage is bound to one run, tree hash, app, and device
  provenance; committed as `15abf47`; Remote Verify `40475288809` passed. All
  7 S04 unit tests were green at that baseline.
- S04 recovery and overwrite hardening: incompatible pending records, query
  uncertainty, ambiguous roots, and unconfirmed deletion now fail closed;
  overwrite fixtures are marker-owned and idempotently cleaned. Commits
  `103b64d` and `23cad83` passed Remote Verify `40488714646` and
  `40491590364`. All 13 S04 unit tests are green.
- S01.4/S02.4/S04.3 independent review found five P1 correctness gaps in
  initialization rollback, cleanup confirmation, stale completion handling,
  overwrite evidence accumulation, and recovery read failures. Commit
  `ad470f5` closes those gaps and related fail-closed cases. Current unit-test
  counts are S01 19, S02 9, and S04 16.
- Spike release isolation commit `80e5a11` blocks direct and transitive
  production-module dependencies on `:spikes:*`, enforces distinct probe/helper
  application IDs, and rejects shared UIDs. Remote Verify `40630087003` passed.
- S05.1: dependency-free Kotlin/JVM benchmark contract classifies complete,
  failed, pending, and invalid thumbnail runs and freezes the fallback order
  from prefetch to cache to cell size. Commit `1183739` passed Remote Verify
  `40633881235`.
- S05.2a: synchronized frame/fetch/OOM recorder, nearest-rank P95, rounded-up
  millisecond metrics, sticky OOM, and post-completion mutation rejection were
  committed as `662a0fa`; Remote Verify `40638974591` passed. All 10 S05 tests
  are green. No Android fetcher or production cache is implemented.
- S05.2b: isolated `com.galleryorganizer.spikes.s05.harness` APK generates
  deterministic app-private PNG fixtures, automatically scrolls a bounded
  grid, records fetch/frame/heap/OOM metrics through S05.2a, and writes bounded
  provenance plus results under its own `filesDir`. Commit `4e06c44` passed
  Remote Verify `40646181752`; debug/release harness tests are green and the
  permission-free debug APK passed v2 signature verification.
- S05.2b completion hardening: version `0.1.1` stops fetch/frame sampling before
  recorder completion, ignores late grid fetches, and reports result-write
  failures in-page instead of exiting. Commit `7fcc3d9` passed Remote Verify
  `40656014739`; all 4 harness tests pass for debug and release variants.
- S05.2c: harness result schema 2 adds manufacturer, model, API level, Android
  release, security patch level, and a SHA-256 hash of the build fingerprint,
  so exported evidence binds to one physical device without leaving the raw
  `Build.FINGERPRINT`. Harness version `0.1.2` is committed as `290e4a5` and
  passed Remote Verify `40667099458`.
  `scripts/verify-s05-evidence.ps1` rejects schema drift, raw fingerprint
  fragments, identity fields, fixture drift, duplicate device hashes, and PASS
  claims without sufficient samples or with failures; aggregate GO is printed
  only for four distinct PASS rows. `scripts/test-s05-evidence.ps1` keeps those
  rules under negative test and runs in Remote Verify. All 6 harness tests pass
  for debug and release variants; the `0.1.2` debug APK passed v2 signature
  verification.
- S05.2c export affordance: version `0.1.3` is committed as `0f8089e`. The
  verification host has no `adb`, so the harness now offers a permission-free
  `Copy result JSON` action that copies the compact schema-2 result out of
  app-private storage through the clipboard. All 7 harness tests pass for debug
  and release variants and the `0.1.3` debug APK passed v2 signature
  verification (2,427,971 bytes, SHA-256
  `36477B4F6FAC9845489EB94828A2474B79D54935583164B5D88F8294BE6ED126`). Remote
  Verify passed for the code and documentation commits; run identifiers were not
  captured because the GitHub API rate limit was exhausted.
- S05.2c measured row: `docs/evidence/s05/target.json` records a vivo V2304A
  (Android 16, API 36, patch 2026-08-01) run as PASS with 360 frame samples,
  664 fetch samples, zero failures, frame P95 17 ms, fetch P95 3 ms, and
  7,103,984 bytes peak used heap, inside every provisional budget limit.
  `scripts/verify-s05-evidence.ps1` accepts the row and still reports
  `PENDING (1/4 rows)`; Android 11, OEM family A, and OEM family B remain
  unmeasured, so budgets are not frozen and this is not aggregate GO.
- Device matrix deferral: Android 11, OEM family A, and OEM family B physical
  runs were explicitly deferred by the user. This is `DEFERRED_BY_USER`, not
  PASS or GO, and does not authorize F05 storage capabilities, I01/I02/I04,
  J1/J2/J3, or any production storage adapter.
- F05.1 no-I/O shell: added deterministic onboarding/settings state, safe
  default scan scopes, an opt-in organizer-root scope, and a flat Compose
  working surface. Interaction hardening commit `7f2b922` adds dedicated scan
  scope, archive location, protected directory, trash retention, and storage
  status screens; settings changes remain in memory, archive choices navigate
  to their configuration screen, and Finish opens a Library shell. Selection,
  adoption, probing, persistence, and storage execution remain unimplemented.
  Presentation has no SAF, MediaStore, URI, activity-result, DAO, or file-I/O
  integration. Eight reducer tests pass in debug and release variants; the
  full 672-task Gradle gate, policy scripts, and architecture negative tests
  pass. The debug APK passed v2 signing verification (25,699,839 bytes,
  SHA-256
  `684C1421F33A5769B92F118BF10C40449068C60637D87B3A922C489F398498F2`).
  Baseline implementation commits `e073468` and `468901b` passed Remote Verify
  `40811346709`; interaction hardening and its state record passed Remote
  Verify `40815766754`.
- F05.1 no-I/O workspace extension: commit `4a3201f` adds a bounded in-memory
  album search, library filters and sort selection, explicit deferred-storage
  empty state, and an Inbox status surface. Finishing setup switches navigation
  to Library/Inbox/Settings while preserving the pending device capability.
  This is not I06c, I01, I04, Paging, thumbnail, database, scan, or storage
  execution. Nine setup tests and three library tests pass in debug and release
  variants; the full 672-task Gradle gate, policy scripts, architecture
  negative tests, release dependency policy, and APK v2 signature verification
  pass locally. The debug APK is 25,639,207 bytes with SHA-256
  `298CCB78A0157D22C06DDBAC5EC6C118C1C5E3069F867E63CA2B4873AD878895`.
  State commit `d51142e` passed Remote Verify `40823326474`.
- F05.1 blocked-workspace extension: commit `72fdf8c` adds Settings entries and
  navigable empty-state shells for Trash and Recovery. Both preserve workspace
  state while exposing no restore, cleanup, deletion, authorization, Journal,
  or storage action. This is not R01 or R02. Ten setup tests pass in debug and
  release variants; the full 672-task Gradle gate, baseline/app/release policy,
  architecture and S05 evidence guards, and APK v2 signature verification pass
  locally. The debug APK is 25,576,539 bytes with SHA-256
  `5E92DC6469F3F46FABF3767C11CCA082D286401AB2F58239FAD3229E04F99E3B`.
  State commit `1d1f36a` passed Remote Verify `40829128941`.
- F05.1 notification settings shell: commit `33bdceb` adds an in-memory
  notification preference with Off, operation-status, and cleanup-reminder
  scopes. The dedicated Settings page keeps Android notification permission
  explicitly `NOT_REQUESTED`; it does not add a Manifest permission, permission
  request, system-settings intent, persistence, scheduler, or notification
  sender. Eleven setup tests pass in debug and release variants. The full
  672-task Gradle gate, policy and architecture guards, release dependency
  policy, and APK v2 verification pass locally. The debug APK is 25,816,339
  bytes with SHA-256
  `BB27A72073D7CD667D56F2301213CEE2D7A61A0EE15A4355D914436BA344E860`.
  State commit `ba910fa` passed Remote Verify `40832222918`.
- F05.1 Inbox decision preview: commit `42c9b3a` adds three bounded example
  source items and memory-only Keep, Review, and Trash draft decisions. Unknown
  source IDs are ignored, individual decisions and the full draft can be
  cleared, and the deferred-storage state remains explicit. The preview cannot
  scan media, persist decisions, create a Plan, or execute storage operations.
  Three Inbox reducer tests pass in debug and release variants; Remote Verify
  `40838346156` passed.
- F05.1 existing-root adoption preview: commit `e96ed8f` adds explicit
  not-requested, not-applicable, and device-gate-pending adoption states. The
  archive and storage-status screens expose root-marker, album-manifest, and
  workspace-conflict checks as not checked while validation is deferred.
  Switching back to a new root clears any adoption claim. No directory is
  opened, no `root.json` or manifest is read, and no SAF, URI, DAO, or file API
  is present in presentation. All 18 presentation tests pass in debug and
  release variants; the full 672-task Gradle gate, policy and architecture
  guards, release dependency policy, and APK v2 verification pass locally.
  The debug APK is 25,846,899 bytes with SHA-256
  `1B2433EED6592E7564765BF12F6D756C53C6E902DBB0CC35539BAF727FF30FE1`.
  Remote Verify `40842839922` passed.
- F05.1 setup review: commit `3045249` adds a separate in-memory review step
  between onboarding and the workspace preview. It summarizes scan scopes,
  archive mode, deferred adoption checks, protected-directory count, trash
  retention, and notification preference without entering the workspace or
  enabling a device capability. All 19 presentation tests pass in debug and
  release variants; the full 672-task Gradle gate, baseline/app/release policy,
  architecture and S05 evidence guards, and APK v2 verification pass locally.
  The debug APK is 25,856,531 bytes with SHA-256
  `9586D14F239329475F1EC734C54EE8D29C36C87D254918C0E8D28221148DB9A1`.
  Remote Verify `40846088847` passed.
- F05.1 import strategy settings: commit `24e4bd5` exposes the existing domain
  `ImportStrategy` through a memory-only Settings page and setup review. The
  safe default is `COPY_KEEP_SOURCE`; `MOVE_SOURCE` can be selected as a future
  plan preference, while P0-hidden `INDEX_IN_PLACE` is not rendered and is
  rejected by the reducer. No ORG or SourceCleanupPlan is created, no cleanup
  authorization is requested, and no preference is persisted. All 21
  presentation tests pass in debug and release variants; the full 672-task
  Gradle gate, baseline/app/release policy, architecture and S05 evidence
  guards, and APK v2 verification pass locally. The debug APK is 25,866,027
  bytes with SHA-256
  `B50FB346608AD38F35EB2D3CE7A04C5744CBD56FC31880907B0B289CDB6F187F`.
  Remote Verify `40849285812` passed.
- F05.1 Library memory preview: commit `c559a28` adds three bounded example
  albums, derived search/filter/sort results, and memory-only album selection.
  The Library labels these rows as examples rather than scanned data; unknown
  album IDs are ignored and selection can be cleared. No scanner, database,
  Paging, thumbnail, Plan, or storage capability is connected. All 22
  presentation tests pass in debug and release variants; the full 672-task
  Gradle gate, baseline/app/release policy, architecture and S05 evidence
  guards, and APK v2 verification pass locally. The debug APK is 25,885,995
  bytes with SHA-256
  `32F18ADA396046CA32D3E65E14C966DE4D3DFA97C3FE9654F11CAC73B6200AA6`.
  Remote Verify `40852850011` passed.
- F05.1 Inbox decision summary: commit `982968d` adds derived Keep,
  Review, Trash, and undecided counts for the bounded source-decision preview.
  Counts only include known preview sources; the summary is memory-only and
  cannot create a Plan or execute storage. All 23 presentation tests pass in
  debug and release variants; the full 672-task Gradle gate, baseline/app/
  release policy, architecture and S05 evidence guards, and APK v2
  verification pass locally. The debug APK is 25,887,527 bytes with SHA-256
  `D17A05CAACDC9FF550CF9DD9E84CD39D90806746C0B1F02F0FC66642A1F30483`.
  Remote Verify `40855489794` passed.
- F05.1 Library selection summary: commit `b09c1c7` adds derived selected
  album count and known preview-album selection details to the memory-only
  Library surface. Unknown IDs are excluded from the summary and no archive
  action is exposed. All 23 presentation tests pass in debug and release
  variants; the full 672-task Gradle gate, baseline/app/release policy,
  architecture and S05 evidence guards, and APK v2 verification pass locally.
  The debug APK is 25,888,427 bytes with SHA-256
  `3ADC9BE2D59123AAD384C3A42527D1B832AF34B904FA3D657F13EE1E52DFB633`.
  Remote Verify `40858235712` passed.
- F05.1 Inbox decision coverage: commit `2b937c1` adds a derived
  in-progress/complete coverage state for the known source-decision preview.
  Completion requires every bounded preview source to have Keep, Review, or
  Trash; unknown IDs do not count. All 24 presentation tests pass in debug
  and release variants; the full 672-task Gradle gate, baseline/app/release
  policy, architecture and S05 evidence guards, and APK v2 verification pass
  locally. The debug APK is 25,888,787 bytes with SHA-256
  `A942F08EED57385DA72162B9572A691B8EE79A6579FBB5A9E1F4E1266EEB1BBD`.
  Remote Verify `40991346751` passed.
- F05.1 Inbox draft status: commit `1545255` replaces the UI-facing
  in-progress/complete branching with the reusable memory-only
  `NOT_STARTED`/`IN_PROGRESS`/`COMPLETE` status enum. Clearing the preview
  draft returns to `NOT_STARTED`; unknown source IDs remain ignored. All 24
  presentation tests pass in debug and release variants; the full 672-task
  Gradle gate, baseline/app/release policy, architecture and S05 evidence
  guards, and APK v2 verification pass locally. The debug APK is 25,890,139
  bytes with SHA-256
  `31B5ACF2AECD5474C0563AD74C17D571F1AE07C087D84B26AF498EF65B1F981B`.
  Remote Verify `40993697560` passed (docs run `40993946835`).
- F05.1 Inbox decision summary snapshot: commit `f1c2e68` adds a reusable
  memory-only `InboxDecisionSummary` containing Keep, Review, Trash,
  undecided, decided, and draft-status values. Existing Inbox counters and
  coverage UI now read the same derived snapshot; unknown source IDs remain
  excluded. All 24 presentation tests pass in debug and release variants;
  the full 672-task Gradle gate, baseline/app/release policy, architecture and
  S05 evidence guards, and APK v2 verification pass locally. The debug APK is
  25,893,435 bytes with SHA-256
  `01852A1468CC857BABFF3D1771040E53BAE3E97638E69114800DECD71631D6D8`.
  Remote Verify `40995432131` passed (docs run `40995467763`).
- F05.1 Library selection summary snapshot: commit `348eb80` adds a
  reusable memory-only `LibrarySelectionSummary` for selected preview album
  details, count, and empty/non-empty state. The Library UI now reads the
  same derived snapshot; unknown album IDs and clear-selection behavior remain
  unchanged. All 24 presentation tests pass in debug and release variants;
  the full 672-task Gradle gate, baseline/app/release policy, architecture and
  S05 evidence guards, and APK v2 verification pass locally. The debug APK is
  25,895,499 bytes with SHA-256
  `E0DCA8000DDFE42F75851A3628298FD87C8F7B4B69BE8F3D351B518706B06348`.
  Remote Verify `40996537506` passed (docs run `40996571745`).
- F05.1 Library selection review preview: commit `f60aa38` adds a
  memory-only review surface for selected example albums, including title,
  date, and needs-review state. The surface is explicitly non-executable and
  cannot create an archive Plan. Presentation tests, the full 672-task Gradle
  gate, baseline/app/release policy, architecture and S05 evidence guards, and
  APK v2 verification pass locally. The debug APK is 25,896,623 bytes with
  SHA-256
  `61A0174A2BCF1039580C606FB38BDED9867C2B44CD59730929FE0512C644113C`.
  Remote Verify `40997851627` passed (docs run `40997923202`).
- F05.1 Inbox decision review preview: commit `b21ff96` adds a
  memory-only review surface for decided example sources, showing each
  source label, Keep/Review/Trash decision, and source detail. Undecided
  sources stay out of the review section, and the surface cannot create an
  archive Plan. Presentation tests, the full 672-task Gradle gate,
  baseline/app/release policy, architecture and S05 evidence guards, and APK
  v2 verification pass locally. The debug APK is 25,897,971 bytes with
  SHA-256
  `39CAB386FF29DF3EE8E1EDB999AB7286E8468582DC54D3E5FA98F9F676E3805C`.
  Remote Verify `40999080499` passed (docs run `40999153002`).
- F05.1 Inbox decision review items: commit `9e09925` adds the
  memory-only `InboxDecisionReviewItem` derived collection. It preserves
  preview order, includes only known decided sources, and becomes empty when
  the draft is cleared; the review UI consumes this collection. Presentation
  tests, the full 672-task Gradle gate, baseline/app/release policy,
  architecture and S05 evidence guards, and APK v2 verification pass locally.
  The debug APK is 25,900,411 bytes with SHA-256
  `156008D15B75CB2D622569C60A7F2209AEACF37F5B8B7D52AA9CD417B9721E20`.
  Remote Verify `41000017263` passed (docs run `41000059690`).
- F05.1 workspace review snapshot: commit `745fc6e` adds the memory-only,
  read-only `WorkspaceReviewSnapshot` that combines `LibrarySelectionSummary`
  and `InboxDecisionSummary`. It derives selected album count, decided and
  undecided source counts, inbox draft status, and combined review activity;
  clearing both previews returns it to an empty, not-started snapshot. The
  Library and Inbox screens share a non-executable workspace review section,
  and no Plan is created. All 28 presentation tests pass in debug and release
  variants; the full 672-task Gradle gate, baseline/app/release policy,
  architecture and S05 evidence guards, and APK v2 verification pass locally.
  The debug APK is 25,905,751 bytes with SHA-256
  `FA521D2E11DF73170C4D9009EAFD26E29668180D1AB32DF3BF5895C7C2E2D246`.
  Remote Verify `41002193435` passed.
- F05.1 workspace review summary: commit `575ab40` extends the memory-only
  snapshot with selected-needs-review album count and Keep/Review/Trash source
  counts. The shared Library/Inbox review section displays these derived
  values without creating a Plan or invoking storage. The new reducer/domain
  coverage verifies all three decision categories and selected review albums.
  All 29 presentation tests pass in debug and release variants; the full
  672-task Gradle gate, baseline/app/release policy, architecture and S05
  evidence guards, and APK v2 verification pass locally. The debug APK is
  25,907,095 bytes with SHA-256
  `4E016BADC5EA8CC7810412384216D94EA4B51CCE95A683E3ADF183028B94FC92`.
  Remote Verify `41005610207` passed.
- F05.1 workspace setup snapshot: commit `50316f7` adds a memory-only
  `WorkspaceSetupSummary` to the shared review snapshot. It carries selected
  scope count, protected-directory count, trash retention, import strategy,
  notification preference, and deferred root capability/adoption state.
  Library and Inbox display the setup-derived scope, import, and retention
  values without creating a Plan or invoking storage. All 31 presentation
  tests pass in debug and release variants; the full 672-task Gradle gate,
  baseline/app/release policy, architecture and S05 evidence guards, and APK
  v2 verification pass locally. The debug APK is 25,912,151 bytes with
  SHA-256
  `5297AD1B0DC0606B401ED9D491DDFA740AB440D60AE9F298A600E1D132C7D526`.
  Remote Verify `41008156086` passed.
- F05.1 workspace attention summary: commit `edb8255` adds the memory-only
  `WorkspaceAttentionSummary`, combining selected-needs-review albums with
  Inbox Review and Trash decisions. It derives a total attention-item count
  and an explicit Trash-preview flag; the shared Library/Inbox review surface
  remains read-only and exposes no destructive action. All 33 presentation
  tests pass in debug and release variants; the full 672-task Gradle gate,
  baseline/app/release policy, architecture and S05 evidence guards, and APK
  v2 verification pass locally. The debug APK is 25,914,607 bytes with
  SHA-256
  `C97DBC668C202B31BFB792D5A86D3B6787B543B277EDEF44BE4E3825757F0F8F`.
  Remote Verify `41010791447` passed.
- F05.1 workspace attention items: commit `b615b67` adds a typed, memory-only
  `WorkspaceAttentionItem` list. Selected albums needing review appear first,
  followed by Inbox Review/Trash sources in preview order; ordinary albums and
  Keep decisions are excluded. Stable IDs distinguish album and source items,
  clearing both previews empties the list, and the shared Library/Inbox surface
  remains read-only. All 35 presentation tests pass in debug and release
  variants; the full 672-task Gradle gate, baseline/app/release policy,
  architecture and S05 evidence guards, and APK v2 verification pass locally.
  The debug APK is 25,922,631 bytes with SHA-256
  `64FB308BF61294371439BE1E413CC9309DCD63F537F82D41B0B549C51F151C3B`.
  Remote Verify `41013527081` passed.
- Device-gate resumption audit: F05.1 is complete and no further F05.1
  increments are authorized. The S01/S02/S04 probe suite and S02 external
  fixture helper rebuild successfully, but `adb devices -l` reports no
  connected device. The v2-signed probe-suite APK is 22,666,227 bytes with
  SHA-256
  `8B5C8B08161C6D793FBD08A60F16B8809B60B65054616E2755603F6DA9621E26`;
  the v2-signed helper APK is 10,639,092 bytes with SHA-256
  `BDCC143E73B90DDA7B7A95A5543C3E13CC1801AD06F10847E4181CC9938C6FD6`.
- Offline device export: probe-suite `0.1.1` adds a tested, permission-free
  `Copy result JSON` action and explicit success/failure feedback to S01, S02,
  and S04. S02 and S04 are scrollable on bounded phone screens. S01 has 21,
  S02 has 11, and S04 has 18 green tests per build variant. The five policy
  guards, full 672-task Gradle gate, release dependency policy, and APK v2
  verification pass locally. The probe-suite APK is 22,682,611 bytes with
  SHA-256
  `ED77105CF38B1E12145B7461669245ADD1F917FBDB2B17769429295290E30955`;
  the unchanged helper APK is 10,639,092 bytes with SHA-256
  `BDCC143E73B90DDA7B7A95A5543C3E13CC1801AD06F10847E4181CC9938C6FD6`.
  No ADB or USB-debugging connection is required.
- First offline device triage: the vivo V2304A/API 36 S01 0.1.1 payload is
  `PROBE_DIAGNOSTIC_INCONCLUSIVE`, not device NO_GO. It proves organizer-root
  and directory-layout setup, then reports a masked `IllegalStateException`;
  the old payload cannot distinguish part metadata, write, read, or rename.
  S02 0.1.0 helper produced a blank/no-response flow. Probe-suite `0.1.2`
  therefore exports a privacy-safe S01 `exceptionStage`, while helper `0.1.1`
  launches the S02 Activity directly through a signature permission. The
  permission contract is covered by unit and architecture negative tests.
  All five guards and the full `check lint assembleDebug` gate pass locally.
  Both APKs use v2 signing and the same signing certificate. Probe-suite
  0.1.2 is 22,682,659 bytes with SHA-256
  `4BDD1C2D3A6C06E22C98D0D93C154F530B482BE720662DC7310876A6DE96B909`;
  helper 0.1.1 is 10,652,220 bytes with SHA-256
  `F21EFE688DD90017CF5CFB70C2E47D603F40B59ED7796F7AECFBD1C6089A2F2E`.
- Second offline device triage: probe-suite 0.1.2 reached
  `WEBP_PART_METADATA`. This proves WebP part creation succeeded, but the
  provider returned non-exact metadata and the probe stopped before write,
  rename, collision, performance, and cleanup evidence. This remains
  diagnostically incomplete rather than a device NO_GO. Probe-suite 0.1.3
  classifies binary/WebP part metadata without exporting the actual random
  part name and continues the remaining operations; non-exact metadata still
  fails the single-device outcome. S02 remained blank through the direct
  cross-APK launch, so helper 0.1.2 now copies the fixture token and S02
  provides an explicit paste action. The S02 Activity is non-exported and the
  obsolete signature launch permission is removed. S01 has 26 tests, S02 has
  13 tests, and the fixture contract has one test, all green. The new APKs are
  v2-signed; all five policy/architecture/evidence guards and the full
  `check lint assembleDebug` gate pass. Probe-suite 0.1.3 is 22,698,999 bytes
  with SHA-256
  `93F2A5B1FE78380D94BDFFBBB161AF59EF726CB89B647B23A46B5D04DC663D03`;
  helper 0.1.2 is 10,638,448 bytes with SHA-256
  `82CADE3CC9ABDB6CAF535DF1988B1D62347C4D27CDF551C4BE90EF374D1232AD`.
- Third offline device triage: probe-suite 0.1.3 completed the main S01 flow
  on vivo V2304A/API 36. Binary part metadata was `EXACT`; WebP part metadata
  was `NAME_APPENDED_EXTENSION`. Write/readback, rename/final readback,
  collision characterization, seek, enumeration, and reinstall read/write all
  ran. Force-stop and reboot retries were not run. `cleanupSucceeded=false`
  and `reinstallAdoptionDelete=false` were traced to probe ledger defects:
  renamed URIs remained registered after replacement, and an explicitly
  deleted adoption candidate remained registered for cleanup. Probe-suite
  0.1.4 fixes those false negatives and adds two regression tests; the real
  WebP metadata limitation still fails closed. Helper 0.1.2 still rendered
  blank with no usable button, so helper 0.1.3 creates the fixture and copies
  its token automatically on first launch; the button remains a retry path.
  The supplied S04 payload contained no observations and remains PENDING; it
  is not device evidence and S04 should not be rerun yet. All five guards and
  the full `check lint assembleDebug` gate pass. Both new APKs are v2-signed.
  Probe-suite 0.1.4 is 22,698,995 bytes with SHA-256
  `DBD521649FED42FD5BFA0BBF0EB52A103A4026140D6E4FC0DC2E2A8BFCF71E88`;
  helper 0.1.3 is 10,639,712 bytes with SHA-256
  `B8E50C2A53D64992A4F6C380A232D3107A31D2E3A7153838C9224156682687E3`.
- Fourth offline device triage: probe-suite 0.1.4 on vivo V2304A/API 36
  confirms the cleanup-ledger fix. Main-flow cleanup and reinstall adoption
  read/write/delete all pass, with orphan classification `READ_WRITE_DELETE`;
  no exception occurred. Final WebP rename/readback, seek, collisions, and
  performance evidence remain successful. WebP part metadata is again
  `NAME_APPENDED_EXTENSION`, confirming a provider limitation rather than a
  probe defect. The current WebP `.part` strategy therefore remains FAIL and
  requires a separately tested safe fallback before LIMITED_GO is possible.
  Force-stop and reboot retries are still pending. Helper 0.1.3 successfully
  copied a valid S02 token on-device, but no S02 probe JSON has been returned.
  The repeated empty S04 payload remains PENDING and is not evidence. Remote
  Verify `41377003379` for commit `9ab7840` passed.
- S01 direct-final fallback diagnostic: probe-suite 0.1.5 preserves the
  primary WebP `.part` FAIL semantics and conditionally tests the delivery-plan
  fallback only when part metadata is non-exact. The fallback creates the
  final `.webp` name directly, requires exact metadata and byte readback, uses
  the shared cleanup ledger, and exports a separate fallback outcome. Existing
  0.1.4 state remains decodable. All 30 S01 tests pass after forced
  recompilation; all five guards and the full `check lint assembleDebug` gate
  pass. The v2-signed APK is 22,698,995 bytes with SHA-256
  `A42B05477E22FF0374BC3EC0B2C47C280DA207B9435033B15E8E36E996F67947`.
  Production storage behavior remains disabled.
- Fifth offline S01 update: the returned state has both force-stop and reboot
  retries passing, while its unchanged random filename and measurements plus
  `webpFallbackOutcome=NOT_RUN` prove it is the prior main result updated by
  retry actions, not a 0.1.5 main probe run. Probe-suite 0.1.6 preserves
  previously passed manual retry evidence when and only when the newly
  selected tree URI exactly matches the saved tree URI. A changed root never
  inherits that evidence. This allows one same-root main rerun to collect
  fallback evidence without repeating already completed restart checks. All
  33 S01 tests pass after forced recompilation; all five guards and the full
  `check lint assembleDebug` gate pass. The v2-signed APK is 22,715,379 bytes
  with SHA-256
  `F80DA13ECCB46645D1F934D49E91C84C8D7DBE450F788831A733F20BC58FA703`.
- Sixth offline S01 result: probe-suite 0.1.6 reran the main flow on vivo
  V2304A/API 36 while retaining the same-root restart evidence. The direct
  final WebP fallback reports exact metadata, successful byte readback, cleanup
  success, and `webpFallbackOutcome=PASS`; force-stop, reboot, and reinstall
  read/write/delete also pass. The primary `.part` outcome correctly remains
  FAIL because the provider appends `.webp`. This device is therefore a
  `LIMITED_GO_CANDIDATE` for the direct-final fallback, not an aggregate GO;
  remaining required devices plus S02/S04 are still mandatory.
- S02 reusable clipboard handoff: helper 0.1.4 persists its last published
  token. On launch it reuses the token only when exactly one helper-owned row
  matches path, MIME, display name, published state, and exact payload;
  otherwise it creates and records a new fixture within the creation rollback
  boundary. This lets the blank-screen Helper restore a token after S01 JSON
  overwrites the clipboard.
  All four helper policy tests, all five guards, and the full
  `check lint assembleDebug` gate pass. The v2-signed helper APK is 10,648,876
  bytes with SHA-256
  `A8710555F5B221842B0EE1CA9B97A619A152206D3106DD52E4597DA1D62BC854`.
- First complete S02 device result: vivo/API 36 reports `FULL` permission but
  zero visible rows, no pages, cleanup request failure, and
  `IllegalStateException`. The S02 query has no row filter; the helper fixture
  used a dot-prefixed display name, which is consistent with OEM MediaStore
  hiding the row from another UID. Treat this as a probe-fixture defect, not
  device NO_GO. S02.5 changes the shared contract to a visible
  `s02-external-<token>.png` name. Probe-suite 0.1.7 and helper 0.1.5 must be
  installed together; helper validation rejects the previously hidden row and
  creates a new visible fixture. Contract, S02 model, and helper tests pass,
  as do all five guards and the full `check lint assembleDebug` gate. Both APKs
  are v2-signed: suite is 22,715,383 bytes with SHA-256
  `D9735668E9B8B7730A3E6E0411AE5E52712FE86BA6C4611BE37C7BCEAD460169`;
  helper is 10,648,876 bytes with SHA-256
  `033F27934887681FFD1B967CE9E0BE1E759C2DDE608CB74661755ACFB49D356F`.
- S02.5 retry still reports `FULL`, `REQUEST_FAILED`, and
  `IllegalArgumentException`, but its zero-row output is not reliable: the
  catch path replaced all partial evidence with a new empty result. S02.6
  fixes this diagnostic defect. It retains completed pagination and observed
  fixture provenance, returns invalid fixture fields directly, and exports
  `exceptionStage` as `QUERY_PAGES`, `FIXTURE_LOOKUP`, or `DELETE_REQUEST`.
  Probe-suite 0.1.8 is the final S02 audit build; helper 0.1.5 remains paired.
  The new S01 run with restart fields false does not invalidate the prior
  completed restart evidence, which remains recorded for the same device/root.
  All staged-failure tests, all five guards, and the full
  `check lint assembleDebug` gate pass. The v2-signed suite APK is 22,715,383
  bytes with SHA-256
  `DE71F298EDA143F3DCE1E21A44127F9E533CBABC718D9EA1C4EB23ABACC462F2`.
- S02.6 preserves the actual device evidence: 142,997 visible rows across 286
  bounded pages; the final page has 497 rows; IDs are strictly increasing,
  duplicate count is zero, and all query args are honored. Pagination therefore
  passes. The visible fixture is found with exact path, MIME, token-bound name,
  and a blank `OWNER_PACKAGE_NAME`; payload checking was skipped only because
  the owner column was redacted. S02.7 permits a redacted owner to enter payload
  validation, but accepts it only when the random token, path, MIME, display
  name, and full payload all match. Any other non-empty owner remains rejected.
  Probe-suite 0.1.9 also copies a compact page summary instead of hundreds of
  page records. Helper 0.1.5 remains paired. Redacted-owner and compact-output
  tests, all five guards, and the full `check lint assembleDebug` gate pass.
  The v2-signed suite APK is 22,715,383 bytes with SHA-256
  `21895D8F4776767BAACEF538E92BF69E735C6BDE5C04FF7883B29675DC6EE1CB`.
- S02.7 current-target result: PASS on vivo V2304A/API 36. FULL permission
  exposes 142,997 rows over 286 bounded pages; IDs are monotonic, duplicate
  count is zero, all query args are honored, and the slowest page is 23 ms.
  The fixture owner column is redacted, while exact path, MIME, token-bound
  display name, and full payload match. One-item delete authorization was
  granted and post-consent absence was confirmed (`GRANTED_REMOVED`). No
  exception or failure stage is present. This closes current-target S02 only;
  S04 and the remaining required device rows still block aggregate device GO.
- S04 guided current-target build: probe-suite 0.1.10 derives one legal action
  from durable S04 state, displays completed cutoff count, requires an actual
  process restart before enabling observation, and hides reset throughout
  partial progress. PASS export and diagnostic export are labeled separately.
  All 24 S04 tests pass after forced recompilation. Probe semantics and storage
  mutation behavior are unchanged. The v2-signed APK is 22,731,771 bytes with
  SHA-256
  `97C95C5A40BD597350662332596F2326092E7C199F86D488429EA390402E7F4F`.
- First guided S04 attempt on vivo/API 36 failed before any cutoff observation
  with `IllegalStateException`; `pendingStagePresent=false` confirms the owned
  sandbox was cleaned. The unextended `.s04-owner` filename was inconsistent
  with its `application/json` MIME type on a provider already proven to append
  MIME extensions. Probe-suite 0.1.11 / S04.4 writes
  `.s04-owner.json`, reads both new and legacy marker names, and exports an
  exact `exceptionStage` for every prepare operation. All 26 S04 tests pass
  after forced recompilation. The v2-signed APK is 22,731,767 bytes with
  SHA-256
  `7E6E4FD0119750EC6F2E64F7642EA002E062591C7D54E3BA17E8535D98A31753`.
- The first S04.4 restart observation on vivo/API 36 successfully recovered
  `FINAL`, found one parseable candidate, and cleaned its sandbox. The run
  failed only because the overwrite candidate `.s04-overwrite.json.tmp` used
  JSON MIME with a non-JSON suffix and triggered `ERROR_IllegalStateException`.
  Probe-suite 0.1.12 / S04.5 uses `.s04-overwrite.tmp.json`, retains legacy
  cleanup ownership, and scopes future overwrite failures to an exact
  operation. All 28 S04 tests pass after forced recompilation. The failed
  overwrite evidence is sticky, so device retry requires `Start new run`. The
  v2-signed APK is 22,731,771 bytes with SHA-256
  `A9B2B6494249AF4A7F4C7D11F5AAB1B73C78CF0676ADA9CBDFB4ADBE70DB5EC9`.
- S04.5 on vivo/API 36 confirms first-cutoff recovery and overwrite
  `AUTO_RENAMED`, then fails the second cutoff exactly at `CREATE_TMP`.
  `manifest.json.tmp` repeated the provider-sensitive JSON MIME/non-JSON
  suffix mismatch. Probe-suite 0.1.13 / S04.6 now writes
  `manifest.tmp.json` and `manifest.bak.json`; recovery retains both legacy
  names. All 29 S04 tests pass after forced recompilation. The app-version
  provenance change requires one `Start new run` before retry. The v2-signed
  APK is 22,731,771 bytes with SHA-256
  `DC87765B7140E2014FC29E3F3202528BBFA96859BBAEF3083808C19FF9C728A6`.
- S04.6 current-target result: PASS on vivo V2304A/API 36. All four cutoff
  observations share run `33d9507d-494a-476c-a672-bcdd2af1809e` and one tree
  hash; restart and cleanup are true throughout. Recovery selected `FINAL`,
  `FINAL`, `TMP`, and `FINAL`, provider overwrite is `AUTO_RENAMED`, and no
  exception or pending stage remains. Evidence is archived at
  `docs/spikes/S04/results/target.json`. This closes current-target S04 only;
  the remaining required device rows still block aggregate device GO.
- F06: pure algorithm/fixture baseline committed as `71fc9ef`; Remote Verify
  `39934313216` passed. Identity invariant hardening commit `6585754` passed
  Remote Verify `40437932838`. Subgroup path and case-insensitive ID collision
  commit `ca03b9d` passed Remote Verify `40440140530`. Relative-path and
  fixture-loader commit `6fdd89f` passed Remote Verify `40445342619`.
- F06 manifest archive pipeline: strict JSON parser (`4fb0649`, Verify
  `40454071478`), manifest codec (`2c99d22`, check `122028203216`), golden
  fixture corpus (`bb070e2`, check `122031243215`), pure filesystem verifier
  (`5a3abff`, check `122036651621`), and manifest factory plus progressive
  pure-domain E2E (`881f023`, check `122047757478`) all passed Remote Verify.
  F06 is COMPLETE within its pure-domain scope.

## Next Exact Action

- S02 current-target evidence is complete and requires no further retry.
- Resolve the remaining required device matrix, or approve a formal ADR that
  changes those release gates. Current-target S04 must not be rerun.
- Only after the required device GO may work resume on full F05, followed by
  N01 and its downstream I01/I02 path. Do not add more F05.1 preview features.

## Active Gates

- F06: COMPLETE; golden manifest fixtures and the progressive pure-domain E2E
  are delivered. Storage executors stay outside F06 and remain gated.
- S01: CURRENT_TARGET_LIMITED_GO_CANDIDATE; direct-final fallback, cleanup,
  restart, and reinstall checks pass on vivo/API 36. Aggregate decision remains
  pending the other required devices and mandatory S04 evidence.
- S02: CURRENT_TARGET_PASS; pagination, redacted-owner token payload
  provenance, bounded cleanup authorization, and confirmed removal all pass on
  vivo/API 36. Aggregate matrix coverage remains incomplete.
- S04: CURRENT_TARGET_PASS; all four cutoff observations, restart detection,
  candidate recovery, cleanup, provenance, and closed provider overwrite
  behavior pass on vivo/API 36. Aggregate matrix coverage remains incomplete.
- S05: ANDROID_HARNESS_COMPLETE / DEVICE_EVIDENCE_PARTIAL; S05.1/S05.2a remain
  pure and CI-green, S05.2b is isolated from production, and S05.2c binds each
  result to one device with a reviewed guard. The `target` row is PASS; three
  required rows are missing, so final budgets are not frozen and I04 remains
  blocked.
- J1/J2/J3 and production storage execution remain BLOCKED until required
  device decisions are recorded.

## Device Matrix Ownership

- Owner: USER
- Planned date: DEFERRED
- Device list: current-target S05 row PASS; Android 11, OEM family A, and OEM
  family B DEFERRED_BY_USER. Required status is retained for release gating.

## Decisions

- Use Kotlin, Android Gradle Plugin, and Jetpack Compose only as the app UI
  baseline. No network-related dependency is permitted.
- Keep application storage behavior disabled until S01/S02/S04 device GO.
- F02 creates only contracts and dependency bindings; it does not enable
  business persistence, source cleanup, or filesystem behavior.
- F02.1 uses Hilt/KSP for composition; bindings remain empty until F04 defines
  fake implementations.
- F03 domain contracts are platform-neutral and must remain independent of
  Room, MediaStore, SAF, WorkManager, and filesystem APIs.
- F04 Fake ports remain test-only; the release Hilt graph must not bind them.
- F04 Room stores app-private metadata only and does not authorize external
  storage, disk Journal, Manifest, copy, cleanup, or deletion behavior.
- F04.1 adds no VirtualClock because no current Port accepts time.
- F04.3 may prove only in-memory integration semantics; S01/S02/S04 remain
  mandatory before J1/J2/J3 or any production storage executor.
- F04 is complete; this does not authorize production storage execution.
- S01 probe code stays in the separate `:spikes:s01-saf` library, installed
  only through `:spikes:probe-suite`, and must not enter the main release graph.
- S01 exported result JSON must never include the raw `Build.FINGERPRINT`;
  only manufacturer, security patch level, and a SHA-256 fingerprint hash may
  leave the device.

## Deviations

- The system installer is blocked by the device organization policy. The
  project uses an isolated JDK 17 and Android SDK under `D:\WorkFile\.tools`
  instead of a system installation.
- System-level JDK installation remains blocked by device policy; local builds
  use the project-local toolchain.
- The source `Baseline Doc/` directory remains untracked. Its verified,
  repository-controlled copy is `docs/spec/`.

## Known Issues

- S01 force-stop/reboot and safe-fallback evidence remain required. The 0.1.4
  result confirms cleanup and reinstall deletion while reproducing the real
  WebP metadata limitation. S02 token creation works, but S02 still has no
  probe result. Other required rows remain deferred.
- S05 has one accepted `target` row and three deferred rows; the recorded
  budget values remain provisional until the matrix completes.
- J1/J2/J3 and all production workspace/Manifest/cleanup execution are blocked
  by those device decisions.
