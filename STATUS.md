# Status

## Current Task

- ID: F06
- Sub-step: Pure Domain Algorithms And Fixtures
- State: IN_PROGRESS

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
- F06: pure algorithm/fixture baseline committed as `71fc9ef`; Remote Verify
  `39934313216` passed. Identity invariant hardening commit `6585754` passed
  Remote Verify `40437932838`. Subgroup path and case-insensitive ID collision
  commit `ca03b9d` passed Remote Verify `40440140530`. Relative-path and
  fixture-loader commit `6fdd89f` passed Remote Verify `40445342619`; task
  remains IN_PROGRESS.

## Next Exact Action

- Independently review S01.2/S02.1/S04.1, then assign and schedule the REQUIRED
  physical-device matrix.

## Active Gates

- F06: IN_PROGRESS; expand only through pure domain behavior and fixtures.
- S01: REVIEW_PENDING; S01.2 ownership repair is CI-green; do not install until
  reviewed.
- S02: REVIEW_PENDING; S02.1 evidence repair is CI-green; device result is
  PENDING.
- S04: REVIEW_PENDING; S04.1 restart repair is CI-green; device result is
  PENDING.
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
- S01 result JSON still exposes the full build fingerprint; replace it with a
  fingerprint hash before results are committed.
- F06 Golden Manifest and progressive pure-test E2E fixtures remain pending.
- J1/J2/J3 and all production workspace/Manifest/cleanup execution are blocked
  by those device decisions.
