# ADR 0001: Use SQLite with WAL Mode and Foreign Keys

## Context
The take-home evaluation requires a self-contained, runnable service that does not require installing external database servers like PostgreSQL. However, concurrency and data integrity must still be guaranteed.

## Decision
We utilize SQLite with HikariCP connection pooling, configured with:
1. `PRAGMA journal_mode = WAL;` (Write-Ahead Logging) to allow concurrent readers while a writer commits.
2. `PRAGMA busy_timeout = 5000;` to ensure write contention waits gracefully before failing.
3. `PRAGMA foreign_keys = ON;` on every initialized connection to guarantee relational referential integrity at the engine level.

## Consequences
- **Positive:** Zero external setup required; fast execution; robust concurrent reads and single-writer ordering.
- **Trade-offs:** SQLite remains single-writer. For distributed multi-instance production environments, this would be swapped with PostgreSQL using Flyway migrations.
