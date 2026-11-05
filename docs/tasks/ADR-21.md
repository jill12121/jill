# ADR-21: Split I06c Production UI Integration

## Decision

- I06c is split into I06c.1 Inbox/Library integration and I06c.2
  Album/Metadata integration.
- I06c.1 uses the existing Room v1 source metadata, decision, bounded page,
  and MediaStore thumbnail contracts. It does not invent album records or
  metadata plans.
- The Inbox keeps at most the newest 96 loaded source items. Decision state
  and Compose thumbnail references are pruned to the same visible window.
- Library uses a truthful production empty state until an album catalog
  contract exists.
- I06c.2 requires explicit album catalog and METADATA Plan contracts before
  production UI is connected.
- I06c.2 derives a bounded catalog only from validated SAF manifest evidence.
  It persists `METADATA/CREATED` plans in the existing app-private Room v1
  `plans` table and never executes them.
- Metadata plan identity is deterministic per album and committed manifest
  revision, so Room rejects duplicate plans across process restarts.

## Rationale

The current schema can truthfully render discovered source batches and
persisted source decisions, but it has no production album catalog or
METADATA Plan lifecycle. Splitting the work prevents preview data from being
presented as persisted content and keeps long Inbox sessions memory-bounded.

## Boundaries

No Room schema change, workspace mutation, source cleanup, Journal, recovery
executor, or external file write is authorized. Android 11, OEM family A, and
OEM family B remain supported but `OWNER_ACCEPTED_DEFERRED`; release remains
blocked by their missing device evidence.

## Status

Accepted by owner direction on 2026-11-03 and completed locally on
2026-11-04 for I06c development only.
