# ADR 0005: Typed Relational Tables for Transaction Metadata

## Context
Transactions span multiple distinct domains (Flights, Hotels, Rail, Navan platform fees), each with unique schemas, collections (coupons, legs, extras), and validation requirements.

## Decision
Instead of storing metadata as untyped JSON blobs in the `transactions` table, we use dedicated relational tables:
- `flight_metadata` and `flight_coupons` (with order preservation)
- `hotel_metadata` and `hotel_extras`
- `rail_metadata` and `rail_legs`
- `navan_fee_metadata`

Each metadata table is mapped via `@MapsId` and cascading one-to-one/one-to-many relationships.

## Consequences
- **Positive:** Type safety, queryability, database-level schema constraints, and clear domain separation.
- **Trade-offs:** Additional table joins during full transaction hydration.
