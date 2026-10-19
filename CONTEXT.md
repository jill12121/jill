# Gallery Organizer Context

## Baseline

- Sole implementation baseline: [`docs/spec/README.md`](docs/spec/README.md).
- Primary contract: v0.3.1. Older documents do not resolve conflicts.
- Platform: Android 11+, `minSdk 30`, current stable target.
- Privacy: no account, no upload, no `INTERNET`, no
  `MANAGE_EXTERNAL_STORAGE`, and `allowBackup=false`.

## Current Delivery Boundary

- Current task: F02, module boundaries, DI skeleton, and architecture checks.
- Before S01, S02, and S04 receive human-device GO, file behavior runs only
  against Fake implementations. No real workspace write is enabled.
- P0a excludes OCR, automatic QR scanning, similar-image analysis, video,
  network, and account dependencies.

## Non-Negotiable Invariants

1. Source files are read only through `SourceReader`; source deletion is a
   separate authorized cleanup plan.
2. Every target write follows Plan -> disk Journal -> WorkspaceGateway ->
   Manifest -> Room.
3. Every deletion uses an independent Plan and Journal.
4. UI and ViewModels never access DAOs, Android storage APIs, or file ports.
5. Capability changes are shown to the user; they are never silently degraded.

## Working Rules

- One session handles one task sub-step with approximately 500 net changed
  lines or fewer.
- Write acceptance tests before implementation.
- Keep the main branch buildable and tests green; isolate incomplete work.
- Read only this file, `STATUS.md`, and the current task file before starting a
  task. Consult baseline specs by relevant line range only when needed.
- Complete each task with unit tests, one fault path, fixture acceptance,
  implementation record, risk, and deviation notes.
