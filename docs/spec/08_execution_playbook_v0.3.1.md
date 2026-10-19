# Execution Playbook v0.3.1

## INV

```text
INV1 Source only read by SourceReader; source deletion only SourceCleanup
INV2 target appears only after disk Journal PLANNED
INV3 source cleanup needs committed manifest + source/target SHA + unchanged source locator
INV4 Room rebuilds from disk Journal + Manifest + files
INV5 scanner/analyzer never write UserState/SourceDecision
INV6 sweep only FULL + completed scan
INV7 operationId=planId; execute once
INV8 Trash purge only PermanentDeletePlan + matching sidecar
INV9 logger never contains raw path/OCR/contact
INV10 final filename exists only after completed .part validation
```

## Archive sequence

```text
lock operationId
→ disk Journal PLANNED(plan + sourceLocator, no URI)
→ create immutable album dir
→ .nomedia first
→ Manifest IMPORTING
→ per file: fingerprint → .part tee SHA → target postcheck → final rename → Room DONE
→ disk watermark every 200 files/2min
→ Manifest COMMITTED + parse validate
→ Room transaction
→ disk Journal COMPLETED
→ optional SourceCleanupPlan
```

## Source cleanup

```text
for each candidate:
 target readback SHA == ImportRecord.sha256
 source size/mtime still matches sourceLocator
 manifest committed
then resolve URI and request authorization in chunks <=300
re-query after result
missing => CLEANED
present => CLEANUP_DENIED
changed => SOURCE_CHANGED
```

Pending auth is persisted; Recovery Center has a todo row. No PendingIntent is persisted.

## Scanner

```text
first run: choose first-level Download folders
root loose files: disabled virtual batch
MediaStore keyset page _ID > lastId limit 500
coverage FULL only permits sweep
PARTIAL allows selected-file import only
exclude organizerRoot + protected dirs
```

## Test requirements

- FaultPlan: throw/partial-write/kill at operation N;
- property test random Plan + crashes: no source deletion pre-verify; valid final/part state; recovery convergence;
- E2E each slice, not only release;
- OEM matrix Android11/current/2 OEM; OCR has no-GMS device;
- update Spike report with go/no-go and ADR impact.

## Task runtime

- short user task: foreground coroutine;
- long user task: TaskExecutionDialog foreground/background;
- foreground-capable Worker checkpoint 50 files/30sec;
- disk watermark 200 files/2min;
- system cleanup/recovery no dialog; notification + recovery todo;
- notification denied: persist PAUSED/continue action.
