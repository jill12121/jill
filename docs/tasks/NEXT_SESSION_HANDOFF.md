# Next Session Handoff

## Repository State

- Repository: `D:\WorkFile\gallery-organizer-v0.3.1`
- Branch: `master`
- Remote: `origin git@github.com:jill12121/jill.git`
- Latest remote commit: `113e6b8 F02.2: fix release policy on PowerShell Core`
- GitHub Actions: Verify run `39365176154` passed on `113e6b8`.

## Completed Baseline

- F01: Android project, privacy baseline, controlled specifications, and CI.
- F02/F02.1: module boundaries, capability-neutral ports, Hilt/KSP composition,
  Android Library module types, and architecture guards.
- F03/F03.1: platform-neutral states, errors, permissions, typed Plan payloads,
  scan coverage, authorization waiting, and exhaustive transition tests.
- F02.2: standard Gradle Wrapper, checksum, executable mode, EOL rules,
  launcher icon, Linux CI, release merged-Manifest policy, and release runtime
  dependency policy.

Relevant commits:

- `7047eb7 F03.1: correct domain state and typed plan contracts`
- `8b4c8a7 F02.2: harden release policy and delivery reproducibility`
- `113e6b8 F02.2: fix release policy on PowerShell Core`

## Current Task

F04 is `PLANNED`. Read `docs/tasks/F04.md` before implementation.

Start with F04.1:

1. Write acceptance tests for deterministic operation sequencing.
2. Add test-only in-memory implementations of the four storage ports.
3. Add `FaultPlan` throw/partial-write/kill simulation by operation number.
4. Prove no source deletion, false completion, real file access, or sleeps.
5. Run domain, architecture, policy, lint, and build verification.

Only after F04.1 is green, continue to Room schema and uniqueness constraints.

## Hard Boundary

F04 permits app-private Room metadata and test-only in-memory Fakes. It does
not permit:

- MediaStore, SAF, `ContentResolver`, `DocumentFile`, or Download scanning.
- Organizer-root creation, `.nomedia`, `.part`, Manifest, or disk Journal I/O.
- Real copy, rename, SHA reads, restore, cleanup, or deletion.
- WorkManager, foreground services, OCR, networking, accounts, or uploads.
- Fake bindings in the release Hilt graph.

S01 blocks workspace/copy execution, S02 blocks scanning/source cleanup, and
S04 blocks disk Journal/recovery execution.

## Verification Environment

Local builds use:

```text
JAVA_HOME=D:\WorkFile\.tools\temurin-17\jdk-17.0.20.1+1
ANDROID_HOME=D:\WorkFile\.tools\android-sdk
GRADLE_USER_HOME=%LOCALAPPDATA%\gallery-organizer-gradle-f03
```

Use `%LOCALAPPDATA%` for Gradle caches. Project-local caches under
`D:\WorkFile\.tools` previously caused transform-cache move failures.

## Completion Report

```text
Task: <ID and title>
State: <COMPLETED / CONDITIONALLY_ACCEPTED / BLOCKED>
Commit: <sha or not committed>
Verification: <commands and result>
Remote CI: <run id/status or not confirmed>
Risk: <remaining risk>
Deviation: <baseline deviation, if any>
Next: <one exact next action>
```
