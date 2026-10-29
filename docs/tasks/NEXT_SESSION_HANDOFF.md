# Next Session Handoff

## Authority

Read `STATUS.md` first. It is the current authority for task state and gates.
Do not infer device GO from CI or desktop tests.

## Verified Code Baseline

- S01.2 `e4b5710`: probe-owned SAF sandbox; Remote Verify `40444298467`.
- F06 `6fdd89f`: relative source identity and loaded TSV fixtures; Remote
  Verify `40445342619`.
- S02.1 `dec1951`: runtime permission, Bundle pagination evidence, controlled
  cleanup authorization; Remote Verify `40446516962`.
- S04.1 `45cecff`: persisted cutoff and post-restart recovery observation;
  Remote Verify `40447648083`.
- S01.3 `5dd458e`: raw build fingerprint replaced by manufacturer, security
  patch, and SHA-256 hash in exported JSON; Remote Verify `40450678249`.
- S01.4 `c44e511`: malformed ownership evidence is rejected and all created
  documents enter a failure-safe cleanup ledger; Remote Verify `122067814285`.
- S02.2 `941efd4`: strict bounded monotonic pagination, honored query
  arguments, cleanup tri-state, and explicit fixture ownership; Remote Verify
  `40469408370`.
- S02.3 `14e6e9e`: pending cleanup survives Activity recreation and evidence is
  bound to schema/probe/run/app/device provenance; Remote Verify `40471142170`.
- S01/S02 hardening `2ed4e7f`: failure-safe cleanup, retained completion, and
  fail-closed pagination defaults; Remote Verify `40482946612`.
- S04.2 `31f24dd`: durable PREPARING/READY/OBSERVED phases and resumable
  post-observation cleanup; Remote Verify `40472524359`.
- S04.3 `15abf47`: content-based overwrite classification and run/root/device
  provenance binding; Remote Verify `40475288809`.
- S04 recovery `103b64d` and overwrite retry-safety `23cad83`: ambiguous,
  incompatible, or uncertain state requires retry and owned fixtures are
  idempotent; Remote Verify `40488714646` and `40491590364`.
- S02.4 `a9ef42a`: separate-UID fixture helper and strict owner/path/MIME/name/
  token/payload validation; Remote Verify `40623103758`.
- Independent spike review `ad470f5` and release isolation `80e5a11`: five P1
  findings were closed and Remote Verify `40630087003` passed.
- S05.1 `1183739`: dependency-free thumbnail benchmark classifier and ordered
  fallback contract; Remote Verify `40633881235` passed.
- S05.2a `662a0fa`: thread-safe frame/fetch/OOM recorder and nearest-rank P95
  aggregation; Remote Verify `40638974591` passed.
- S05.2b `4e06c44`: isolated permission-free Android app-private fixture and
  fetch/scroll harness; Remote Verify `40646181752` passed.
- S05.2b hardening `7fcc3d9`: version `0.1.1` prevents late fetches from
  touching a completed recorder; Remote Verify `40656014739` passed.
- S05.2c `290e4a5`: schema-2 result JSON binds each run to manufacturer, model,
  API level, Android release, security patch level, and a SHA-256 fingerprint
  hash (no raw `Build.FINGERPRINT`); `scripts/verify-s05-evidence.ps1` and its
  negative guard `scripts/test-s05-evidence.ps1` review the exported matrix.
  Remote Verify `40667099458` passed.
- S05.2c export `0f8089e`: version `0.1.3` adds a permission-free
  `Copy result JSON` action so results can leave an app-private directory
  without `adb`; 7 harness tests pass per variant.
- F06 manifest pipeline `4fb0649` -> `2c99d22` -> `bb070e2` -> `5a3abff` ->
  `881f023`: strict JSON parser, manifest codec, golden fixtures, filesystem
  verifier, and manifest factory plus progressive pure-domain E2E; latest
  check `122047757478` succeeded. F06 is COMPLETE.

S01.4/S02.4/S04.3 independent review is complete. Physical-device evidence is
still required before any aggregate device decision.

## Current State

- F01-F04: complete safety and test foundation.
- F06: `COMPLETE` within its pure-domain scope; golden manifest fixtures and
  the progressive pure-domain E2E are delivered (35 domain tests green).
- S01/S02/S04: `DEVICE_PENDING`; independent review is complete.
- Spike unit tests: S01 19, S02 9, S04 16, S05 core 10, S05 harness 7 per
  build variant; all green.
- S05.1/S05.2a/S05.2b/S05.2c: `COMPLETE`; physical-device fetch/scroll
  evidence is PENDING. I04 remains blocked.
- F05/I01/I02/J1/J2/J3 and production storage execution: BLOCKED.

## Next Exact Action

1. Assign owner, date, and concrete devices for Android 11, current target,
   OEM family A, and OEM family B.
2. Execute the reviewed physical-device matrix.
3. Record per-device JSON and GO/LIMITED_GO/NO_GO inputs.
4. Install and execute the isolated S05.2b harness on the device matrix,
   collect `s05-results/latest.json`, and review budgets/fallbacks without
   wiring it into production.
5. Store each exported result as `docs/evidence/s05/{android11,target,oem-a,
   oem-b}.json` and run `scripts/verify-s05-evidence.ps1`. Four distinct PASS
   rows are required for aggregate GO; one iQOO 11S row is not sufficient.

## Hard Boundaries

- No production SAF, MediaStore, WorkspaceGateway, ManifestStore,
  PermanentDeleter, disk Journal, copy, rename, cleanup, or recovery executor.
- No Room schema change.
- No J1/J2/J3 before required device decisions and ADRs.
- F06 additions must remain pure Kotlin/domain and fixture driven.
- S05.2b does not authorize I04, production ThumbnailSource, cache persistence,
  external media access, or Paging integration.

## Verification Environment

```text
JAVA_HOME=D:\WorkFile\.tools\temurin-17\jdk-17.0.20.1+1
ANDROID_HOME=D:\WorkFile\.tools\android-sdk
GRADLE_USER_HOME=D:\WorkFile\.tools\gradle-home-f02-1-serial
```

The Kotlin daemon may report an AccessDenied temp-marker warning on this host;
Gradle falls back to in-process compilation. Judge the final task result, not
that warning alone.
