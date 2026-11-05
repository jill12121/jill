# S03 Device Evidence

Archive compact JSON copied from the isolated S03 harness as:

- `middle-tier.json`: immutable historical schema-2 middle-tier evidence.
- `middle-tier-schema3.json`: aggregate diagnostic rerun on that device
  (`label=100`, `numeric=0`, measured `FAIL`).
- `middle-tier-schema4.json`: same-device paired Noto Sans rerun
  (`plain=50/50`, `padded=49/50`, measured `PASS`).
- `no-gms-schema4.json`: a distinct physical device without GMS Core, only
  after the schema-4 middle-tier result is classified.

The verifier prefers schema 4, then schema 3, while retaining schema 2
compatibility. Schema 4 requires the exact `real-font-paired-v2` provenance
and consistent plain/padded pair aggregates. Missing rows report `PENDING`;
any measured failure reports `FAIL`. Never add raw expected or recognized OCR
text.

The schema-4 middle-tier evidence has precision and recall 995/1000, zero
sample failures, and P95 36 ms. Its SHA-256 is
`B0FDA7695689167F354E661E40F566D87D95EE536906F1E3B66493EE776DDDAD`.
This shows the schema-3 numeric collapse was primarily a 5x7 corpus artifact,
not a systematic inability to recognize zero-padded real-font values. The
matrix remains `PENDING (1/2 rows; missing: no-gms)`.
