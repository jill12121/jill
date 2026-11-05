# ADR-26: B04.5 Default-disabled Android OCR Adapter

## Decision

The owner accepts the development risk of adding the bundled ML Kit text
recognition dependency before the missing no-GMS S03 row exists. B04.5 may
implement an Android `ManualOcrRecognizer` under `data/ocr`, but production
availability remains disabled and the adapter remains unreachable.

This decision permits compilation and local tests only. It is not evidence
that the bundled model works without GMS and does not authorize release.

## Adapter Contract

- The default policy is disabled and must return `ManualOcrRecognition.Failed`
  before source access or model creation.
- A caller-supplied authorization boundary must approve each opaque
  `contentKey`; only `mediastore:image:<positive id>` sources are accepted.
- Encoded input is capped at 20 MiB. Source dimensions are capped at 40 MP,
  and decode sampling targets a 2048 px edge and at most 4 MP.
- Recognition is limited to 10 seconds. Timeout, decode, provider, model, and
  rule failures return the same masked failure.
- Caller cancellation is rethrown. Bitmap and recognizer resources are
  released on success, failure, timeout, and cancellation.
- Raw recognized text exists only between the model and an injected rule set.
  It must not cross the adapter, enter exceptions or logs, or be persisted.
- Results contain at most 16 domain-validated hits for the requested key.

## Disabled Production Surface

- No Hilt binding, app factory, UI action, Room write, worker, scheduler, or
  background execution is added.
- No production advertisement rule set is supplied. Rule definitions require
  a separate reviewed product specification.
- `ManualOcrUiState.production()` remains
  `DISABLED_PENDING_NO_GMS_EVIDENCE`.

## Guards

Architecture verification allows the ML Kit API and dependency only in
`data/ocr`, requires the adapter's default policy to be disabled, and keeps
`ManualOcrRecognizer` absent from the app composition module.

## Exit

Feature enablement still requires a valid no-GMS S03 row, reviewed production
rules, an explicit composition decision, and device tests covering decode,
timeout, cancellation, and model initialization.
