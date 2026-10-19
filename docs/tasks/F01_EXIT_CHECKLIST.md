# F01 Exit Checklist

## Repository Completeness

- [x] Copy the complete v0.3.1 document package into `docs/spec/`.
- [x] Change `CONTEXT.md` to use the repository-relative baseline path.
- [ ] Configure the repository-local Git author name and email.
- [ ] Commit all staged F01 files as `F01: project skeleton and policy guard`.

## Build and CI

- [x] Align CI push triggers with both `main` and current `master`.
- [x] Require policy verification, lint, unit tests, and debug APK assembly.
- [x] Verify Gradle locally with JDK 17 and Android SDK 35.
- [ ] Push the F01 commit and confirm the GitHub Actions run.

## F02 Start Gate

- [x] Confirm the repository contains `docs/spec/README.md` and all v0.3.1
  baseline files.
- [ ] Confirm a clean working tree after the F01 commit.
- [ ] Start only the F02 module-boundary sub-step; do not add business
  persistence or real file I/O.
