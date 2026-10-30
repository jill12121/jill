# Next Session Handoff

## Authority

Read `STATUS.md` first. It is the current authority for task state and gates.
Do not infer device GO from CI or desktop tests.

## Verified Code Baseline

- S01.2 `e4b5710`: probe-owned SAF sandbox; Remote Verify `40444298467`.
- F06 `6fdd89f`: relative source identity and loaded TSV fixtures; Remote
  Verify `40445342619`.
- S02.1 `dec1951`: runtime permission, Bundle pagination evidence, controlled
  cleanup authorization; Remote Verify `40446516962`.
- S04.1 `45cecff`: persisted cutoff and post-restart recovery observation;
  Remote Verify `40447648083`.
- S01.3 `5dd458e`: raw build fingerprint replaced by manufacturer, security
  patch, and SHA-256 hash in exported JSON; Remote Verify `40450678249`.
- S01.4 `c44e511`: malformed ownership evidence is rejected and all created
  documents enter a failure-safe cleanup ledger; Remote Verify `122067814285`.
- S02.2 `941efd4`: strict bounded monotonic pagination, honored query
  arguments, cleanup tri-state, and explicit fixture ownership; Remote Verify
  `40469408370`.
- S02.3 `14e6e9e`: pending cleanup survives Activity recreation and evidence is
  bound to schema/probe/run/app/device provenance; Remote Verify `40471142170`.
- S01/S02 hardening `2ed4e7f`: failure-safe cleanup, retained completion, and
  fail-closed pagination defaults; Remote Verify `40482946612`.
- S04.2 `31f24dd`: durable PREPARING/READY/OBSERVED phases and resumable
  post-observation cleanup; Remote Verify `40472524359`.
- S04.3 `15abf47`: content-based overwrite classification and run/root/device
  provenance binding; Remote Verify `40475288809`.
- S04 recovery `103b64d` and overwrite retry-safety `23cad83`: ambiguous,
  incompatible, or uncertain state requires retry and owned fixtures are
  idempotent; Remote Verify `40488714646` and `40491590364`.
- S02.4 `a9ef42a`: separate-UID fixture helper and strict owner/path/MIME/name/
  token/payload validation; Remote Verify `40623103758`.
- Independent spike review `ad470f5` and release isolation `80e5a11`: five P1
  findings were closed and Remote Verify `40630087003` passed.
- S05.1 `1183739`: dependency-free thumbnail benchmark classifier and ordered
  fallback contract; Remote Verify `40633881235` passed.
- S05.2a `662a0fa`: thread-safe frame/fetch/OOM recorder and nearest-rank P95
  aggregation; Remote Verify `40638974591` passed.
- S05.2b `4e06c44`: isolated permission-free Android app-private fixture and
  fetch/scroll harness; Remote Verify `40646181752` passed.
- S05.2b hardening `7fcc3d9`: version `0.1.1` prevents late fetches from
  touching a completed recorder; Remote Verify `40656014739` passed.
- S05.2c `290e4a5`: schema-2 result JSON binds each run to manufacturer, model,
  API level, Android release, security patch level, and a SHA-256 fingerprint
  hash (no raw `Build.FINGERPRINT`); `scripts/verify-s05-evidence.ps1` and its
  negative guard `scripts/test-s05-evidence.ps1` review the exported matrix.
  Remote Verify `40667099458` passed.
- S05.2c export `0f8089e`: version `0.1.3` adds a permission-free
  `Copy result JSON` action so results can leave an app-private directory
  without `adb`; 7 harness tests pass per variant. Remote Verify passed.
- F06 manifest pipeline `4fb0649` -> `2c99d22` -> `bb070e2` -> `5a3abff` ->
  `881f023`: strict JSON parser, manifest codec, golden fixtures, filesystem
  verifier, and manifest factory plus progressive pure-domain E2E; latest
  check `122047757478` succeeded. F06 is COMPLETE.

S01.4/S02.4/S04.3 independent review is complete. Android 11, OEM family A,
and OEM family B physical-device runs are DEFERRED_BY_USER, not GO.

## Current State

- F01-F04: complete safety and test foundation.
- F06: `COMPLETE` within its pure-domain scope; golden manifest fixtures and
  the progressive pure-domain E2E are delivered (35 domain tests green).
- S01/S02/S04: `DEVICE_DEFERRED_BY_USER`; independent review is complete.
- Spike unit tests: S01 19, S02 9, S04 16, S05 core 10, S05 harness 7 per
  build variant; all green.
- S05.1/S05.2a/S05.2b/S05.2c: `COMPLETE`; the `target` device row is measured
  and PASS (`docs/evidence/s05/target.json`), while Android 11, OEM family A,
  and OEM family B are DEFERRED_BY_USER. I04 remains blocked.
- F05.1: navigable no-I/O onboarding/settings/library shell is complete
  locally. Eight reducer tests pass per variant. Settings rows open dedicated
  in-memory editors, archive choices open their configuration screen, and
  Finish opens the Library shell. No action executes a storage capability.
  Commit `7f2b922` passes the full 672-task Gradle gate, policy scripts,
  architecture negative tests, and APK v2 signature verification locally;
  state commit `177a483` passed Remote Verify `40815766754`.
- F05.1 workspace extension: commit `4a3201f` adds in-memory Library
  search/filter/sort controls, a deferred-storage empty state, an Inbox status
  surface, and post-setup Library/Inbox/Settings navigation. Nine setup tests
  and three library tests pass per variant. No Paging, thumbnail, database,
  scan, or storage capability is enabled. Full local gates and APK v2 signature
  verification pass; state commit `d51142e` passed Remote Verify `40823326474`.
- F05.1 blocked-workspace extension: commit `72fdf8c` adds Settings navigation
  to explicit Trash and Recovery empty states. Ten setup tests pass per variant.
  These shells provide no restore, cleanup, deletion, authorization, Journal,
  or storage action and do not complete R01/R02. The full 672-task Gradle gate,
  policy and architecture guards, release policy, and APK v2 verification pass
  locally; state commit `1d1f36a` passed Remote Verify `40829128941`.
- F05.1 notification settings shell: commit `33bdceb` adds a three-level
  in-memory notification preference and a dedicated editor. Eleven setup tests
  pass per variant. Android notification permission remains `NOT_REQUESTED`;
  no Manifest permission, permission request, system-settings intent,
  persistence, scheduler, or notification sender is implemented. Full local
  gates and APK v2 verification pass; state commit `ba910fa` passed Remote
  Verify `40832222918`.
- F05.1 Inbox decision preview: commit `42c9b3a` adds three bounded example
  source items with memory-only Keep, Review, and Trash draft decisions.
  Unknown source IDs are ignored and drafts can be cleared without creating a
  Plan. Three Inbox reducer tests pass in debug and release variants; Remote
  Verify `40838346156` passed. No media scan, persistence, Plan execution, or
  storage capability is enabled.
- F05.1 existing-root adoption preview: commit `e96ed8f` adds explicit
  not-requested, not-applicable, and device-gate-pending adoption states plus a
  read-only checklist for root marker, album manifests, and workspace
  conflicts. Switching to a new root removes the adoption claim. All 18
  presentation tests pass per variant; the full 672-task local gate, policy
  and architecture guards, release policy, and APK v2 verification pass.
  Remote Verify `40842839922` passed. The preview does not open a directory or
  read `root.json`; SAF, URI, DAO, and file APIs remain absent from
  presentation.
- F05.1 setup review: commit `3045249` adds a separate in-memory confirmation
  step that summarizes the setup draft before entering the workspace preview.
  Review preserves scope, root, adoption, protected-directory, retention, and
  notification choices while capability state remains pending. All 19
  presentation tests pass per variant; the full 672-task local gate, policy
  and architecture guards, S05 evidence guard, and APK v2 verification pass.
  The debug APK is 25,856,531 bytes with SHA-256
  `9586D14F239329475F1EC734C54EE8D29C36C87D254918C0E8D28221148DB9A1`;
  Remote Verify `40846088847` passed. No persistence, SAF, MediaStore, URI,
  Journal, copy, cleanup, recovery, Paging, or thumbnail behavior is enabled.
- F05.1 import strategy settings: commit `24e4bd5` adds a memory-only default
  import strategy editor and includes the choice in setup review. It defaults
  to `COPY_KEEP_SOURCE`, permits `MOVE_SOURCE`, and rejects the P0-hidden
  `INDEX_IN_PLACE` value. All 21 presentation tests pass per variant; the full
  672-task local gate, policy and architecture guards, S05 evidence guard, and
  APK v2 verification pass. The debug APK is 25,866,027 bytes with SHA-256
  `B50FB346608AD38F35EB2D3CE7A04C5744CBD56FC31880907B0B289CDB6F187F`;
  Remote Verify `40849285812` passed. The setting does not create a Plan,
  request cleanup authorization, persist state, or access storage.
- F05.1 Library memory preview: commit `c559a28` adds three bounded example
  albums, derived search/filter/sort behavior, and memory-only selection with
  clear support. All 22 presentation tests pass per variant; the full 672-task
  local gate, policy and architecture guards, S05 evidence guard, and APK v2
  verification pass. The debug APK is 25,885,995 bytes with SHA-256
  `32F18ADA396046CA32D3E65E14C966DE4D3DFA97C3FE9654F11CAC73B6200AA6`;
  Remote Verify `40852850011` passed. The examples are explicitly not scan
  results; no scanner, Room, Paging, thumbnail, Plan, or storage behavior is
  enabled.
- F05.1 Inbox decision summary: commit `982968d` adds derived Keep, Review,
  Trash, and undecided counts for known preview sources. All 23 presentation
  tests pass per variant; the full 672-task local gate, policy and architecture
  guards, S05 evidence guard, and APK v2 verification pass. The debug APK is
  25,887,527 bytes with SHA-256
  `D17A05CAACDC9FF550CF9DD9E84CD39D90806746C0B1F02F0FC66642A1F30483`;
  Remote Verify `40855489794` passed. The summary remains memory-only and
  does not create a Plan, persist decisions, or access storage.
- F05.1 Library selection summary: commit `b09c1c7` adds derived selected
  album count and known preview-album details to the memory-only Library
  surface. All 23 presentation tests pass per variant; the full 672-task
  local gate, policy and architecture guards, S05 evidence guard, and APK v2
  verification pass. The debug APK is 25,888,427 bytes with SHA-256
  `3ADC9BE2D59123AAD384C3A42527D1B832AF34B904FA3D657F13EE1E52DFB633`;
  Remote Verify `40858235712` passed. No archive action, Plan, persistence, or
  storage behavior is enabled.
- F05.1 Inbox decision coverage: commit `2b937c1` adds a derived coverage
  state that becomes complete only after every known preview source receives a
  Keep, Review, or Trash decision. All 24 presentation tests pass per variant;
  the full 672-task local gate, policy and architecture guards, S05 evidence
guard and APK v2 verification pass. The debug APK is 25,888,787 bytes with
  SHA-256 `A942F08EED57385DA72162B9572A691B8EE79A6579FBB5A9E1F4E1266EEB1BBD`;
  Remote Verify `40991346751` passed. The state remains memory-only and does
  not create a Plan, persist decisions, or access storage.
- F05.1 Inbox draft status: commit `1545255` adds reusable
  `NOT_STARTED`/`IN_PROGRESS`/`COMPLETE` state derivation for the bounded
  memory-only source-decision preview. Clearing the draft returns to
  `NOT_STARTED`; unknown source IDs remain ignored. All 24 presentation tests
  pass per variant; the full 672-task local gate, policy and architecture
  guards, and APK v2 verification pass. The debug APK is 25,890,139 bytes
  with SHA-256
  `31B5ACF2AECD5474C0563AD74C17D571F1AE07C087D84B26AF498EF65B1F981B`.
  Remote Verify `40993697560` passed (docs run `40993946835`).
- F05.1 Inbox decision summary snapshot: commit `f1c2e68` adds the reusable
  memory-only `InboxDecisionSummary` for Keep, Review, Trash, undecided,
  decided, and draft-status values. The Inbox UI and compatibility accessors
  use the same derived snapshot, while unknown source IDs remain excluded.
  All 24 presentation tests pass per variant; the full 672-task local gate,
  policy and architecture guards, and APK v2 verification pass. The debug APK
  is 25,893,435 bytes with SHA-256
  `01852A1468CC857BABFF3D1771040E53BAE3E97638E69114800DECD71631D6D8`.
  Remote Verify `40995432131` passed (docs run `40995467763`).
- F05.1 Library selection summary snapshot: commit `348eb80` adds the
  reusable memory-only `LibrarySelectionSummary` for selected preview album
  details, count, and empty/non-empty state. The Library UI reads this same
  derived snapshot; unknown album IDs and clear-selection behavior remain
  unchanged. All 24 presentation tests pass per variant; the full 672-task
  local gate, policy and architecture guards, and APK v2 verification pass.
  The debug APK is 25,895,499 bytes with SHA-256
  `E0DCA8000DDFE42F75851A3628298FD87C8F7B4B69BE8F3D351B518706B06348`.
  Remote Verify `40996537506` passed (docs run `40996571745`).
- F05.1 Library selection review preview: commit `f60aa38` adds a
  memory-only review surface for selected example albums with title, date,
  and needs-review state. It is explicitly non-executable and cannot create
  an archive Plan. Presentation tests, the full 672-task local gate, policy and
  architecture guards, and APK v2 verification pass. The debug APK is
  25,896,623 bytes with SHA-256
  `61A0174A2BCF1039580C606FB38BDED9867C2B44CD59730929FE0512C644113C`.
  Remote Verify `40997851627` passed (docs run `40997923202`).
- F05.1 Inbox decision review preview: commit `b21ff96` adds a memory-only
  review surface for decided example sources, showing source label,
  Keep/Review/Trash decision, and source detail. Undecided sources remain out
  of the review section, and no archive Plan can be created. Presentation
  tests, the full 672-task local gate, policy and architecture guards, and APK
  v2 verification pass. The debug APK is 25,897,971 bytes with SHA-256
  `39CAB386FF29DF3EE8E1EDB999AB7286E8468582DC54D3E5FA98F9F676E3805C`.
  Remote Verify `40999080499` passed (docs run `40999153002`).
- F05.1 Inbox decision review items: commit `9e09925` adds the memory-only
  `InboxDecisionReviewItem` derived collection. It preserves preview order,
  includes only known decided sources, becomes empty when the draft is cleared,
  and is consumed by the review UI. Presentation tests, the full 672-task
  local gate, policy and architecture guards, and APK v2 verification pass.
  The debug APK is 25,900,411 bytes with SHA-256
  `156008D15B75CB2D622569C60A7F2209AEACF37F5B8B7D52AA9CD417B9721E20`.
  Remote Verify `41000017263` passed (docs run `41000059690`).
- F05.1 workspace review snapshot: commit `745fc6e` adds the memory-only,
  read-only `WorkspaceReviewSnapshot.from(libraryState, inboxState)` combining
  `LibrarySelectionSummary` with `InboxDecisionSummary`. It exposes selected
  album count, decided/undecided source counts, inbox draft status, and
  combined review activity, and returns to an empty, not-started snapshot
  after both previews are cleared. Library and Inbox share a non-executable
  workspace review section; it creates no Plan and performs no I/O. All 28
  presentation tests pass per variant; the full 672-task local gate, policy
  and architecture guards, S05 evidence guard, and APK v2 verification pass.
  The debug APK is 25,905,751 bytes with SHA-256
  `FA521D2E11DF73170C4D9009EAFD26E29668180D1AB32DF3BF5895C7C2E2D246`;
  Remote Verify `41002193435` passed.
- F05.1 workspace review summary: commit `575ab40` extends the memory-only
  snapshot with selected-needs-review album count and Keep/Review/Trash source
  counts. The shared Library/Inbox review section displays the derived values;
  it creates no Plan and performs no I/O. New presentation coverage verifies
  all decision categories and selected review albums. All 29 presentation
  tests pass per variant; the full 672-task local gate, policy and
  architecture guards, S05 evidence guard, and APK v2 verification pass.
  The debug APK is 25,907,095 bytes with SHA-256
  `4E016BADC5EA8CC7810412384216D94EA4B51CCE95A683E3ADF183028B94FC92`;
  Remote Verify `41005610207` passed.
- F05.1 workspace setup snapshot: commit `50316f7` adds a memory-only
  `WorkspaceSetupSummary` to the shared review snapshot. It carries selected
  scope count, protected-directory count, trash retention, import strategy,
  notification preference, and deferred root capability/adoption state.
  Library and Inbox display the setup-derived scope, import, and retention
  values; no Plan is created and no I/O is performed. All 31 presentation
  tests pass per variant; the full 672-task local gate, policy and
  architecture guards, S05 evidence guard, and APK v2 verification pass.
  The debug APK is 25,912,151 bytes with SHA-256
  `5297AD1B0DC0606B401ED9D491DDFA740AB440D60AE9F298A600E1D132C7D526`;
  Remote Verify `41008156086` passed.
- F05.1 workspace attention summary: commit `edb8255` adds the memory-only
  `WorkspaceAttentionSummary`, combining selected-needs-review albums with
  Inbox Review and Trash decisions. It derives a total attention-item count
  and explicit Trash-preview flag. The shared Library/Inbox review surface is
  read-only and exposes no destructive action. All 33 presentation tests pass
  per variant; the full 672-task local gate, policy and architecture guards,
  S05 evidence guard, and APK v2 verification pass. The debug APK is
  25,914,607 bytes with SHA-256
  `C97DBC668C202B31BFB792D5A86D3B6787B543B277EDEF44BE4E3825757F0F8F`;
  Remote Verify `41010791447` passed.
- F05.1 workspace attention items: commit `b615b67` adds a typed, memory-only
  `WorkspaceAttentionItem` list. Selected albums needing review appear first,
  followed by Inbox Review/Trash sources in preview order; ordinary albums and
  Keep decisions are excluded. Stable IDs distinguish album and source items,
  clearing both previews empties the list, and the shared Library/Inbox surface
  remains read-only. All 35 presentation tests pass per variant; the full
  672-task local gate, policy and architecture guards, S05 evidence guard, and
  APK v2 verification pass. The debug APK is 25,922,631 bytes with SHA-256
  `64FB308BF61294371439BE1E413CC9309DCD63F537F82D41B0B549C51F151C3B`;
  Remote Verify `41013527081` passed.
- Device-gate resumption audit: F05.1 is complete and no further F05.1
  increments are authorized. The S01/S02/S04 probe suite and S02 external
  fixture helper rebuild successfully, but `adb devices -l` reports no
  connected device. The v2-signed probe-suite APK is 22,666,227 bytes with
  SHA-256
  `8B5C8B08161C6D793FBD08A60F16B8809B60B65054616E2755603F6DA9621E26`;
  the v2-signed helper APK is 10,639,092 bytes with SHA-256
  `BDCC143E73B90DDA7B7A95A5543C3E13CC1801AD06F10847E4181CC9938C6FD6`.
- Offline device export: probe-suite `0.1.1` provides `Copy result JSON` with
  feedback for S01/S02/S04 and requires no ADB connection. S01/S02/S04 have
  21/11/18 tests green per variant. The full 672-task gate, five policy guards,
  release policy, and APK v2 verification pass. Probe-suite is 22,682,611
  bytes, SHA-256
  `ED77105CF38B1E12145B7461669245ADD1F917FBDB2B17769429295290E30955`;
  helper SHA-256 remains
  `BDCC143E73B90DDA7B7A95A5543C3E13CC1801AD06F10847E4181CC9938C6FD6`.
- F05/I01/I02/J1/J2/J3 and production storage execution: BLOCKED.

## Next Exact Action

1. Transfer and install the probe-suite and helper APKs without ADB.
2. Run S01, then launch S02 from its external helper, then run S04. Copy and
   return each result JSON through the in-app action.
3. Record device evidence and aggregate decisions. CI and desktop tests are
   not device GO.
4. Resume full F05 only after the required GO, then proceed to N01 and its
   downstream I01/I02 path. Do not add more F05.1 preview features.

## Hard Boundaries

- No production SAF, MediaStore, WorkspaceGateway, ManifestStore,
  PermanentDeleter, disk Journal, copy, rename, cleanup, or recovery executor.
- No Room schema change.
- No J1/J2/J3 before required device decisions and ADRs.
- F06 additions must remain pure Kotlin/domain and fixture driven.
- S05.2b does not authorize I04, production ThumbnailSource, cache persistence,
  external media access, or Paging integration.

## Verification Environment

```text
JAVA_HOME=D:\WorkFile\.tools\temurin-17\jdk-17.0.20.1+1
ANDROID_HOME=D:\WorkFile\.tools\android-sdk
GRADLE_USER_HOME=D:\WorkFile\.tools\gradle-home-f02-1-serial
```

The Kotlin daemon may report an AccessDenied temp-marker warning on this host;
Gradle falls back to in-process compilation. Judge the final task result, not
that warning alone.
