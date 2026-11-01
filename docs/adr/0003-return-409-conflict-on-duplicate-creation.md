# ADR 0003: Return HTTP 409 Conflict with Existing ID on Duplicate External ID

## Context
When an external partner or upstream service retries transaction creation with the same `external_id`, the system must prevent duplicate rows while informing the caller of the duplicate.

## Decision
We enforce a unique index on `transactions.external_id` at the database level. When a concurrent or duplicate insert occurs:
1. The unique constraint throws a `DataIntegrityViolationException`.
2. The service catches the constraint violation and performs a lookup for the winning record's ID.
3. The API responds with `HTTP 409 Conflict` containing both the error message and the winner's `"id"`.

## Consequences
- **Positive:** Guarantees absolute uniqueness even under high concurrency without relying on fragile in-memory locks. Callers immediately obtain the ID of the existing resource.
- **Alternative considered:** HTTP 200 with idempotency replay (Stripe pattern). 409 was chosen here to explicitly signal deduplication to the client as mandated by the project requirements.
