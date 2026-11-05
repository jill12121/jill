# ADR-25: B04.4 OCR Adapter Failure Containment

## Decision

B04.4 may harden the pure domain call boundary before an Android OCR adapter
exists. `ManualOcrRunner` converts ordinary recognizer exceptions into the
existing masked `RECOGNIZER_FAILURE` result and stops before later subjects.
Coroutine cancellation remains cooperative and is rethrown.

This decision does not authorize an Android adapter implementation. The
missing no-GMS S03 row still blocks the production model, source-byte access,
dependency injection, UI wiring, persistence, and feature enablement.

## Rationale

Android decode and model APIs can fail by throwing even when the intended port
contract returns `ManualOcrRecognition.Failed`. Containing ordinary exceptions
at the domain boundary prevents exception detail, including possible source or
recognized-text fragments, from escaping into presentation state.

Cancellation is control flow rather than recognition failure. Converting it
to a failed session would prevent callers from stopping work cooperatively.

## Delivered

- Ordinary `Exception` values become `RECOGNIZER_FAILURE` for the active key.
- Prior masked Evidence and processed progress remain intact.
- Later subjects are not invoked after the exception.
- `CancellationException` is rethrown unchanged.
- The app composition module is guarded against binding
  `ManualOcrRecognizer`.
- S03 and S05 Robolectric tests use module-local writable test homes.

## Prohibited

- Production ML Kit or another OCR engine dependency.
- `ContentResolver`, source-byte, bitmap decode, or model lifecycle wiring.
- Recognized-text rules without a separately reviewed rule specification.
- DI binding, UI action, Room write, automatic/background execution, or
  feature enablement.

## Exit

Implementing the Android adapter still requires the no-GMS S03 row or a new
explicit release-risk decision, plus reviewed decode limits, source
authorization, timeout/cancellation ownership, rule definitions, and resource
closure tests.
