# Status

## Current Task

- ID: F05.1
- Sub-step: Navigable onboarding, settings, library, inbox, trash, and recovery no-I/O shell
- State: COMPLETE; DEVICE_GATES_DEFERRED_BY_USER

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

- Continue F05 only as no-I/O UI/domain work that does not invoke storage
  capabilities or claim full F05 completion.
- Resume the deferred device matrices before enabling root selection,
  adoption, MediaStore scanning, workspace initialization, thumbnails/Paging,
  Journal, copy, cleanup, or recovery execution.

## Active Gates

- F06: COMPLETE; golden manifest fixtures and the progressive pure-domain E2E
  are delivered. Storage executors stay outside F06 and remain gated.
- S01: DEVICE_DEFERRED_BY_USER; independent review is complete and CI-green.
- S02: DEVICE_DEFERRED_BY_USER; external-helper ownership, strict provenance,
  and release isolation are independently reviewed and CI-green.
- S04: DEVICE_DEFERRED_BY_USER; durable recovery, overwrite, and provenance
  hardening are independently reviewed and CI-green.
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

- S01/S02/S04 physical-device results are DEFERRED_BY_USER; emulator and
  desktop results cannot satisfy the REQUIRED matrix.
- S05 has one accepted `target` row and three deferred rows; the recorded
  budget values remain provisional until the matrix completes.
- J1/J2/J3 and all production workspace/Manifest/cleanup execution are blocked
  by those device decisions.
