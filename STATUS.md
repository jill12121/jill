# Status

## Current Task

- ID: F02.1
- Sub-step: Contract, DI, and module-type hardening
- State: COMPLETED

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

## Next Exact Action

- Commit and push F02.1, then confirm its GitHub Actions run before F02.2.

## Decisions

- Use Kotlin, Android Gradle Plugin, and Jetpack Compose only as the app UI
  baseline. No network-related dependency is permitted.
- Keep application storage behavior disabled until S01/S02/S04 device GO.
- F02 creates only contracts and dependency bindings; it does not enable
  business persistence, source cleanup, or filesystem behavior.
- F02.1 uses Hilt/KSP for composition; bindings remain empty until F03
  defines fake implementations.

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
- Lint still reports the expected missing launcher icon; it is deferred to
  F02.2 and does not affect the privacy or backup policy.
