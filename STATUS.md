# Status

## Current Task

- ID: F01
- Sub-step: Android project skeleton and static privacy checks
- State: COMPLETED_WITH_ENVIRONMENT_BLOCKER

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

## Next Exact Action

- Configure the repository-local Git author, commit F01, and verify the remote
  CI run before starting F02.

## Decisions

- Use Kotlin, Android Gradle Plugin, and Jetpack Compose only as the app UI
  baseline. No network-related dependency is permitted.
- Keep application storage behavior disabled until S01/S02/S04 device GO.

## Deviations

- The system installer is blocked by the device organization policy. The
  project uses an isolated JDK 17 and Android SDK under `D:\WorkFile\.tools`
  instead of a system installation.
- Git commit remains blocked until a repository author name and email are
  configured. No author identity was inferred or written automatically.
- The source `Baseline Doc/` directory remains untracked. Its verified,
  repository-controlled copy is `docs/spec/`.

## Known Issues

- F01 is staged but uncommitted because Git has no configured author identity.
