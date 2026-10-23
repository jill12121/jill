# Status

## Current Task

- ID: F06
- Sub-step: Pure Domain Algorithms And Fixtures
- State: IN_PROGRESS

## Completed

- Created a dedicated Git repository at `gallery-organizer-v0.3.1`.
- Added the Kotlin/Compose Android project, Gradle Wrapper, and GitHub Actions
  verification workflow.
- Added policy guard tests for forbidden permissions, network dependencies, and
  `minSdk=30`; all policy tests pass.
- Added a project-local Temurin JDK 17 and Android SDK 35 toolchain under
  `D:\WorkFile\.tools`; `testDebugUnitTest` now passes.
- Ran `lint` and `assembleDebug` successfully using the same project-local
  toolchain. The generated debug APK is a F01 skeleton only, not a feature
  release.
- Added the F01 exit checklist and aligned CI triggers with the current
  `master` branch as well as `main`.
- Copied and hash-verified the complete v0.3.1 specification package into
  `docs/spec/`; `CONTEXT.md` now references that repository-relative baseline.
- Added baseline verification to CI and confirmed its missing-specification
  fault path with a temporary fixture.
- Added `Baseline Doc/` to `.gitignore`; `docs/spec/` is the only
  repository-controlled baseline location.
- Pushed F01 commits through `883c7cd` to `origin/master`; GitHub Actions run
  `39217313578` is reported successful.
- Added the `core:contract`, `domain`, `data`, and `presentation` module
  boundaries, with `app` as the sole composition root.
- Added Konsist and static architecture guards for module direction, domain
  platform isolation, UI port isolation, forbidden deleter dependencies, and
  the F02 real-I/O freeze.
- Closed the F02 review actions: `data` now depends on `domain`, and explicit
  Android 12+ backup/device-transfer exclusion rules are policy-guarded.
- Completed F02.1: Android Library module types, Hilt/KSP composition root,
  typed asynchronous port contracts, behavior-only use cases, a non-empty
  Konsist scope assertion, and a staged I/O guard policy.
- Started F03 with pure Kotlin domain contracts for state machines, plan
  schema validation, source decisions, domain error mapping, and Android media
  permission matrices. Added table-driven tests without Room, fake adapters,
  Android storage APIs, or real file I/O.
- Completed F03.1 review closure: corrected state machines, added typed Plan
  payloads and authorization waiting, separated scan coverage from API
  capability, added operation-aware failure resolution, and exhaustively
  tested all transition matrices.
- Completed F02.2 delivery hardening: standard Gradle Wrapper scripts,
  distribution checksum, executable Git mode, stable EOL rules, launcher icon,
  and CI setup-gradle integration.
- Added fixture-tested release checks for the generated merged Manifest and
  release runtime dependency graph. Local release policy verification, check,
  lint, and debug APK assembly pass.
- Committed and pushed F03.1, F02.2, and the PowerShell Core CI correction.
  GitHub Actions Verify run `39365176154` passed on commit `113e6b8`.
- Completed F04.1 locally with four test-only in-memory Port implementations,
  deterministic operation-index faults, inspectable recovery state, and a
  production Fake-dependency Guard.
- Corrected F04.1 promotion to require a distinct explicit final target and
  made Fake workspace writes reject occupied part/final/trash paths without
  changing source or target state.
- Committed and pushed F04.1.1. Remote Verify run `39381020609` passed on
  commit `3a656dd`.
- Added the app-private Room v1 metadata schema with 15 entities, all frozen
  uniqueness rules, and the specified ImportRecord, Plan, Journal, and
  TrashEntry indexes.
- Added atomic Draft invalidation and archival updates that clear `activeKey`,
  plus local Room tests for insertion conflicts, indexes, schema export, and
  the version 1 migration baseline.
- Committed and pushed F04.2. Remote Verify run `39389514045` passed on commit
  `452f46f`.
- Hardened Draft state/active-key invariants, ImportRecord conflict coverage,
  composite uniqueness allowed branches, and committed-import metadata
  transaction rollback behavior.
- Extended release policy fixtures to reject test-only runtime dependencies
  and production references to test Fake implementations.
- Committed and pushed F04.2.1. Remote Verify run `39401867807` passed on
  commit `bb8c96d`.
- Added the F04.3 construction instruction with test-only Fake, Room
  in-memory, FaultPlan, and integration-test boundaries.
- Completed F04.3 with nine integration scenarios for fingerprint
  invalidation, import linkage, failure mapping, cleanup denial, interrupted
  copy, pre-Manifest recovery input, and in-memory Journal semantics.
- Kept the F04.3 harness test-only and verified zero Room schema diff, zero
  forbidden API hits, 37 passing data tests, all project build gates, and the
  generated release artifact policy.
- Recorded the F04.3 semantic review as NO-GO despite Remote Verify run
  `39567952968` passing the original delivery.
- Implemented F04.3.1 returned-failure injection and logging, source-retention
  observation, full test-only Journal operation/step semantics, and
  `PARTIAL_IMPORT` recovery integration.
- Committed and pushed F04.3.1 as `58a6350`. Remote Verify run `39574777445`
  passed.
- Hardened the isolated S01 probe around a directly selected organizer root,
  production MIME/rename paths, restart/reinstall checks, collisions, flags,
  throughput, seek, enumeration, safe JSON export, and release isolation.
  No human-device result has been recorded.
- Started F06 pure domain algorithms and cross-OEM filename/fingerprint
  fixtures; completion is not yet claimed.

## Next Exact Action

- Complete F06 local and Remote Verify gates; S01/S02/S04 device runs remain
  PENDING.

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
- S01 probe code stays in the separate `:spikes:s01-saf` application and must
  not be bound into the main release graph.

## Deviations

- The system installer is blocked by the device organization policy. The
  project uses an isolated JDK 17 and Android SDK under `D:\WorkFile\.tools`
  instead of a system installation.
- System-level JDK installation remains blocked by device policy; local builds
  use the project-local toolchain.
- The source `Baseline Doc/` directory remains untracked. Its verified,
  repository-controlled copy is `docs/spec/`.

## Known Issues

- F02 remains an architecture-only skeleton; it has no organizer
  functionality, business storage, or real I/O by design.
- J1/J2/J3 remain blocked by S01 SAF, S02 MediaStore, and S04
  Manifest/Recovery device Spikes even after F04 completes.
- S01 is ready for device execution, not complete; no desktop or emulator
  result may be substituted for the required human-device matrix.
