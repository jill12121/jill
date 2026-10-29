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
- F05/I01/I02/J1/J2/J3 and production storage execution: BLOCKED.

## Next Exact Action

1. Continue only no-I/O UI/domain work that does not require a deferred device
   gate or claim full F05 completion.
2. Before enabling any storage capability, resume the reviewed device matrix,
   record the missing evidence, and obtain the required decisions.

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
