# ADR 0002: Snapshot Invoices as Self-Contained JSON Blobs

## Context
Invoices are legal and accounting instruments that represent a frozen point-in-time commercial record. If an underlying transaction is subsequently updated or mutated (where permissible), historical invoices must never reflect those downstream modifications.

## Decision
Invoice lines capture transactional state at creation time by persisting serialized JSON snapshots (`tax_lines_json`, `fee_lines_json`, `metadata_json`), alongside frozen totals and currency. Invoice reads and exports deserialize exclusively from these snapshots, without joining live transaction tables.

## Consequences
- **Positive:** Complete immutability guarantee. Zero risk of retroactive tax or price calculation drifts.
- **Trade-offs:** Snapshot JSON cannot be directly queried with relational SQL joins without SQLite JSON1 functions. Storage is slightly duplicated in exchange for guaranteed historical fidelity.
