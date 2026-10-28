# Status

## Current Task

- ID: S01/S02/S04 and S05
- Sub-step: REQUIRED physical-device matrix; S05.2c measured benchmark run
- State: DEVICE_PENDING; S05.2b HARNESS_COMPLETE

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

- Assign and schedule the REQUIRED physical-device matrix, then execute the
  reviewed S01.4/S02.4/S04.3 runbooks on every required device.
- Install and execute the S05.2b harness on the required physical-device
  matrix, collect `s05-results/latest.json`, and review budgets/fallbacks
  without wiring the harness into the release app.

## Active Gates

- F06: COMPLETE; golden manifest fixtures and the progressive pure-domain E2E
  are delivered. Storage executors stay outside F06 and remain gated.
- S01: DEVICE_PENDING; independent review is complete and CI-green. Required
  device rows remain PENDING.
- S02: DEVICE_PENDING; external-helper ownership, strict provenance, and
  release isolation are independently reviewed and CI-green.
- S04: DEVICE_PENDING; durable recovery, overwrite, and provenance hardening
  are independently reviewed and CI-green.
- S05: ANDROID_HARNESS_COMPLETE / DEVICE_EVIDENCE_PENDING; S05.1/S05.2a remain
  pure and CI-green, and S05.2b is isolated from production. No physical-device
  fetch/scroll evidence exists, so final budgets are not frozen and I04 remains
  blocked.
- J1/J2/J3 and production storage execution remain BLOCKED until required
  device decisions are recorded.

## Device Matrix Ownership

- Owner: UNASSIGNED
- Planned date: UNSCHEDULED
- Device list: Android 11 REQUIRED; current target REQUIRED; OEM family A
  REQUIRED; OEM family B REQUIRED.

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

- S01/S02/S04 physical-device results are PENDING; emulator and desktop
  results cannot satisfy the REQUIRED matrix.
- S05 has an isolated Android harness but no measured physical-device result;
  its current budget values remain provisional classifier inputs.
- J1/J2/J3 and all production workspace/Manifest/cleanup execution are blocked
  by those device decisions.
