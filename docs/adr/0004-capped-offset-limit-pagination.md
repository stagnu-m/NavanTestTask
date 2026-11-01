# ADR 0004: Capped Offset-Limit Pagination for Collections

## Context
Unbounded listing endpoints (`SELECT * FROM transactions`) risk unbounded memory exhaustion and slow response times under growing dataset sizes.

## Decision
1. Endpoints provide default pagination parameters (`limit=50`, `offset=0`).
2. The `limit` parameter is capped at `100`. Requests requesting `limit > 100`, `limit < 1`, or `offset < 0` are rejected with `HTTP 400 Bad Request`.
3. Paginating queries query the dataset with exact limit and offset boundaries.

## Consequences
- **Positive:** Protection against denial-of-service through unbounded dumps. Predictable query performance.
- **Future Evolution:** For multi-million row scale, cursor-based (keyset) pagination using `(occurred_at, id)` indexes will replace `OFFSET` to avoid deep-scan penalties.
