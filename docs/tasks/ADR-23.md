# ADR-23: Deferred no-GMS Evidence And Manual OCR Domain Boundary

## Decision

The owner explicitly deferred the missing no-GMS S03 device row and authorized
the next independent P0b development slice. B04 may proceed only as pure
domain contracts and tests while the production OCR feature remains disabled.

## Allowed

- Explicitly user-initiated manual OCR request contracts.
- Bounded immutable session state and progress transitions.
- Structured rule hits that contain no recognized OCR text.
- Stable, masked Evidence draft generation.

## Prohibited

- Claiming S03 aggregate GO or bundled no-GMS support.
- Automatic, startup, background, or scanner-triggered OCR.
- Production ML Kit dependency or feature-flag enablement.
- Raw OCR text in domain objects, persistence, logs, analytics, or UI state.
- Room schema changes, source-byte wiring, WorkManager, or foreground service.

## Exit

Data and UI integration require a separately reviewed B04 sub-step. Production
enablement still requires the missing no-GMS evidence or a new explicit
release-risk decision.
