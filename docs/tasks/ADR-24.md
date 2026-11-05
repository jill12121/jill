# ADR-24: B04.3 Presentation Boundary And Deferred Android OCR Adapter

## Decision

B04.3 adds a presentation-only manual OCR state machine. Production constructs
it as `DISABLED_PENDING_NO_GMS_EVIDENCE`; no screen, action, dependency
injection binding, source-byte reader, or OCR model is added.

The Android adapter is approved as a design only. Implementation requires a
later boundary review after the missing no-GMS S03 evidence or a new explicit
release-risk decision.

## Presentation Contract

- A domain-valid, explicitly user-initiated request is required.
- The user must confirm before a run can enter `RUNNING`.
- Terminal results must match the active session, request, and content keys.
- Stale session results are ignored; structurally foreign results fail closed.
- UI state stores only progress, unique masked Evidence count, and an enum
  failure reason. It does not store Evidence detail or recognized text.
- Production availability remains disabled and has no UI wiring.

## Deferred Android Adapter Design

A future adapter may live under `data/ocr` and implement
`ManualOcrRecognizer`. Its internal sequence must be:

1. Resolve one approved content key through a separately reviewed, bounded
   source provider.
2. Decode a bounded in-memory image and run the bundled on-device recognizer.
3. Normalize and classify recognized text entirely inside the adapter.
4. Emit at most 16 `ManualOcrRuleHit` values for the requested content key.
5. Release image and recognizer resources on success, cancellation, or error.

Raw text must never cross the adapter, enter logs or analytics, be persisted,
or appear in exceptions. Decode, recognition, timeout, cancellation, and rule
validation failures return `ManualOcrRecognition.Failed`.

## Guards

Architecture verification rejects production ML Kit OCR dependencies and API
imports. The isolated S03 harness remains the only module allowed to depend on
the bundled OCR model.

## Exit

Adapter implementation and DI binding require a new B04 sub-step reviewing
source authorization, decode bounds, dispatcher ownership, timeout and
cancellation behavior, resource closure, model lifecycle, and no-GMS release
evidence. Room writes, automatic/background OCR, and feature enablement remain
out of scope.
