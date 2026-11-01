# Navan Travel Commerce & Invoice Service

A production-grade microservice built with **Java 21**, **Spring Boot 3.3.5**, and **SQLite** for handling structured travel transactions (`flight`, `hotel`, `rail`, `navan_fee`), duplicate detection under concurrency, immutable invoice snapshots, and rollups.

---

## Quickstart

### Prerequisites
- JDK 21 (Eclipse Adoptium / OpenJDK)
- macOS / Linux / Windows

### 1. Run the API (One Command)

**Via Gradle:**
```bash
./gradlew bootRun
```
The application will start on `http://localhost:8080` backed by the SQLite database `./data/travel_invoice.db`.

**Via Docker (No local JDK needed):**
```bash
docker compose up --build
```

### Interactive API Documentation (Swagger UI)
Once running, explore and execute all API requests directly in the browser:
- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI v3 Spec:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### 2. Run All Automated Tests & Check Coverage
```bash
./gradlew test jacocoTestReport
```
Executes the comprehensive automated test suite covering every endpoint, happy path, failure scenario, concurrency race, and freeze rule.
- **Coverage report:** `build/reports/jacoco/test/html/index.html` (All 6 JaCoCo metrics exceed 80%: **96.8% line**, **97.0% instruction**, **87.6% branch**, **90.6% complexity**, **97.8% method**, **100% class**).
- **Enforce >= 80% rule across all metrics:**
```bash
./gradlew jacocoTestCoverageVerification
```

### 3. Continuous Integration (GitHub Actions)
The repository includes a GitHub Actions CI pipeline in [`.github/workflows/ci.yml`](.github/workflows/ci.yml) that triggers automatically on every `push` and `pull_request` to `main`:
- **Automated Execution:** Runs `./gradlew test jacocoTestReport jacocoTestCoverageVerification` on Ubuntu with Temurin JDK 21.
- **Coverage Gate:** Build fails automatically if code coverage drops below the 80% threshold.
- **Step Summary:** Generates an inline Markdown summary in the GitHub Actions run overview with both the unit/integration test results suite breakdown and the 6-metric JaCoCo coverage table.
- **Artifacts:** Uploads both JaCoCo HTML/XML reports and JUnit test results as downloadable job artifacts (14-day retention).

### 4. Manual Testing via `requests.http` & `jsons/` Directory
- **VS Code REST Client / IntelliJ HTTP Client:** See [`requests.http`](requests.http) for runnable, click-to-execute requests covering the entire lifecycle: create -> duplicate conflict -> invoice creation -> freeze rule 409 -> pagination.
- **Pre-built Payloads:** The [`jsons/`](jsons) directory contains pre-built, ready-to-use JSON payloads for manually verifying all endpoints and failure scenarios:
  - **Valid Payloads:** `post_transaction_flight.json`, `post_transaction_hotel.json`, `post_transaction_rail.json`, `post_transaction_trip_fee.json`, `post_transaction_agent_call_fee.json`
  - **Updates:** `patch_transaction.json`, `patch_invoice.json`
  - **Invoices:** `post_invoice_single.json`, `post_invoice_trip.json`
  - **Conflict Scenarios (409):** `post_transaction_duplicate.json`, `post_invoice_mixed_currency.json`, `post_invoice_duplicate_id.json`
  - **Validation Failures (400):** `post_transaction_invalid_type.json`, `post_transaction_invalid_fee_kind.json`, `post_transaction_empty_metadata.json`, `post_transaction_flight_as_hotel_blob.json`, `post_invoice_empty.json`

See [`jsons/README.md`](jsons/README.md) for copy-paste `curl` commands for all payloads.
For key design trade-offs and decisions, see the [Architecture Decision Records (ADRs)](docs/adr).

---

## Endpoint & Testing Matrix

Every endpoint in the service is verified by automated integration tests for both success and failure scenarios:

| Endpoint | Method | Success | Failure Scenarios & Status Codes | Automated Test Method |
|---|---|---|---|---|
| `/transactions` | `POST` | `201 Created` | **400 Bad Request**: unknown `type`, unknown `navan_fee.fee_kind`, empty metadata `{}`, flight as hotel blob, invalid date, malformed JSON, missing fields<br>**409 Conflict**: duplicate `external_id` (sequential & concurrent) returning existing `id` | `test1`, `test2`, `test6`, `test11`, `test20` |
| `/transactions/{id}` | `GET` | `200 OK` | **404 Not Found**: non-existent transaction ID | `test1`, `test13` |
| `/transactions` | `GET` | `200 OK` | **400 Bad Request**: `limit > 100`, `limit < 1`, `offset < 0` | `test10`, `test14` |
| `/transactions/{id}` | `PATCH` | `200 OK` | **404 Not Found**: non-existent transaction ID<br>**409 Conflict**: transaction is included on an invoice (invoiced freeze)<br>**400 Bad Request**: invalid type or date | `test7`, `test15`, `test16`, `test17` |
| `/transactions/{id}` | `DELETE` | `204 No Content` | **404 Not Found**: non-existent transaction ID<br>**409 Conflict**: transaction is included on an invoice (invoiced freeze) | `test8`, `test18`, `test19` |
| `/invoices` | `POST` | `201 Created` | **400 Bad Request**: empty body `{}` or empty list `[]`<br>**404 Not Found**: referenced transaction ID not found<br>**409 Conflict**: mixed currencies (e.g. EUR + USD)<br>**409 Conflict**: duplicate transaction ID in request list | `test3`, `test4`, `test5`, `test21`, `test26` |
| `/invoices/{id}` | `GET` | `200 OK` | **404 Not Found**: non-existent invoice ID | `test3`, `test4`, `test22` |
| `/invoices` | `GET` | `200 OK` | **400 Bad Request**: `limit > 100`, `limit < 1`, `offset < 0` | `test23` |
| `/invoices/{id}` | `PATCH` | `200 OK` | **404 Not Found**: invoice not found, or transaction in list not found<br>**409 Conflict**: duplicate transaction IDs, or mixed currency<br>**400 Bad Request**: empty transaction list | `test24` |
| `/invoices/{id}` | `DELETE` | `204 No Content` | **404 Not Found**: non-existent invoice ID (underlying transactions remain intact) | `test9`, `test25` |
| `/health` | `GET` | `200 OK` (Actuator: `db` + `diskSpace` checks) | **503 Service Unavailable**: database or disk check is `DOWN` | `test12` |

---

## Core Requirements & Policy Guarantees

### 1. Unknown Types and Fee Kinds (HTTP 400)
- Unknown transaction types (e.g. `"spaceship"`) map to `TransactionType.UNSUPPORTED` during JSON deserialization and trigger an immediate **`400 Bad Request`**.
- For `navan_fee` transactions, `metadata.fee_kind` is validated against allowed fee kinds (`trip_fee`, `agent_call_fee`). Any unrecognized or missing kind triggers an immediate **`400 Bad Request`**.

### 2. Metadata Integrity: No Empty Dummies or Cross-Type Storage (HTTP 400)
- **No Empty Dummies:** Submitting `"metadata": {}` or `null` triggers an immediate **`400 Bad Request`**.
- **No Cross-Type Blobs:** Submitting a flight transaction with hotel metadata (or vice versa) is caught by strong structural DTO validation prior to entity creation, returning **`400 Bad Request`**.
  - Flight requires `origin`, `destination`, `fare`, and non-empty `coupons` with flight number and route.
  - Hotel requires `name`, `address` (`street`, `city`, `country`), `check_in`, `check_out`, `nights`, and `room_rate_per_night`.
  - Rail requires non-empty `legs` with route and departure/arrival times.

### 3. Relational Storage (No Untyped `varchar metadata` Blobs)
- All live domain records are stored in dedicated relational tables with foreign keys and indices:
  - `transactions` (header)
  - `transaction_tax_lines` & `transaction_fee_lines`
  - `transaction_flight_metadata` & `transaction_flight_coupons`
  - `transaction_hotel_metadata` & `transaction_hotel_extras`
  - `transaction_rail_metadata` & `transaction_rail_legs`
  - `transaction_navan_fee_metadata`
- Every queryable attribute is an indexed, typed column. `GET /transactions/{id}` round-trips the complete metadata structure matching the seed payloads.

### 4. Atomic Persistence & Server ID Generation
- Persistence of the header row, tax lines, fee lines, and metadata entities is encapsulated within a single Spring `@Transactional` boundary: all persist or none persist.
- Every created transaction returns a server-generated UUID formatted as `txn_<hex>`.

### 5. Duplicates, Concurrency, and Idempotency
- **Unique Constraint:** The `transactions` table enforces a database-level `UNIQUE` constraint on `external_id`.
- **Sequential Duplicates:** Re-posting a known `external_id` queries the unique index and returns **`409 Conflict`** with the existing resource ID (`{"error": "Transaction with external_id already exists", "id": "<existing_id>"}`). Exactly one row is stored.
- **Concurrent Overlapping Creates:** Two or more overlapping parallel `POST /transactions` requests with the same `external_id` race directly against the database unique index without relying on JVM-level synchronized locks. Exactly one request creates the record (`201 Created`); competing parallel requests catch the constraint violation, look up the winner, and return **`409 Conflict`** with the winning transaction's ID.
- **Multiple Invoices:** A transaction can be referenced across multiple invoices if distinct billing line items are issued; however, once included on any invoice, the underlying transaction row is permanently frozen against `PATCH` and `DELETE` modifications.

### 6. Money Scale & Rounding Policy
- All monetary amounts (`total`, tax amounts, fee amounts, fares, room rates, extras, invoice totals, rollups) use `BigDecimal` with a fixed scale of **2 decimal places** and **`RoundingMode.HALF_UP`**.
- Database columns are defined as `NUMERIC(19, 2)`.
- Floating-point IEEE 754 arithmetic is strictly prohibited throughout the codebase.

---

## Example `curl` Commands

### 1. Health Check
Served by Spring Boot Actuator at `/health`. It checks the database connection (`db`) and free disk space (`diskSpace`), and returns **`503 Service Unavailable`** if any check is `DOWN`.
```bash
curl -i http://localhost:8080/health
```
**Response:**
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

---

### 2. Create Transactions (`POST /transactions`)

#### Flight (Using Payload File)
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_flight.json
```

#### Hotel (Using Payload File)
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_hotel.json
```

#### Rail (Using Payload File)
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_rail.json
```

#### Navan Fee - Trip Fee
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_trip_fee.json
```

#### Navan Fee - Agent Call Fee
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_agent_call_fee.json
```

---

### 3. Duplicate Handling (409 Conflict)

Posting an existing `external_id` returns **409 Conflict** with the existing server ID, preventing duplicate records:
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_duplicate.json
```
**Response:**
```json
HTTP/1.1 409 Conflict
Content-Type: application/json

{
  "error": "Transaction with external_id already exists",
  "id": "txn_8e1e791f68744b82bc0e3869273c52e6"
}
```

**Verify Exactly One Row Exists:**
```bash
# Query the list endpoint and filter by external_id to verify exactly one record exists:
curl -s "http://localhost:8080/transactions?limit=100" | jq 'map(select(.external_id == "flight-TXL-LHR-2026-03-11")) | length'
# Output: 1
```

---

### 4. Read Transactions

#### Get Transaction by ID
```bash
curl -X GET http://localhost:8080/transactions/{transaction_id}
```

#### List Transactions (Paginated)
```bash
# Default: limit=50, offset=0
curl -X GET "http://localhost:8080/transactions?limit=50&offset=0"

# Exceeding limit (capped at max 100) returns 400 Bad Request:
curl -i -X GET "http://localhost:8080/transactions?limit=1000"
```

---

### 5. Update and Delete Transaction (Freeze Rules)

#### Update Transaction (when NOT invoiced)
```bash
curl -i -X PATCH http://localhost:8080/transactions/{transaction_id} \
  -H "Content-Type: application/json" \
  -d @jsons/patch_transaction.json
```

#### Update/Delete Transaction when Invoiced (Returns 409 Conflict)
```bash
curl -i -X PATCH http://localhost:8080/transactions/{invoiced_transaction_id} \
  -H "Content-Type: application/json" \
  -d '{ "total": 999.0 }'
```
**Response:**
```json
HTTP/1.1 409 Conflict
Content-Type: application/json

{
  "error": "Transaction is included on an invoice and cannot be updated"
}
```

```bash
curl -i -X DELETE http://localhost:8080/transactions/{invoiced_transaction_id}
```
**Response:**
```json
HTTP/1.1 409 Conflict
Content-Type: application/json

{
  "error": "Transaction is included on an invoice and cannot be deleted"
}
```

---

### 6. Invoices

#### Create Trip Invoice (Multiple Transaction IDs)
```bash
curl -i -X POST http://localhost:8080/invoices \
  -H "Content-Type: application/json" \
  -d '{
    "transaction_ids": [
      "<flight_id>",
      "<hotel_id>",
      "<rail_id>",
      "<trip_fee_id>",
      "<agent_call_fee_id>"
    ]
  }'
```
**Response (Matches `expected-invoice.json`):**
```json
{
  "id": "inv_7d3b259d646b4501a357778b86e8cf65",
  "currency": "EUR",
  "totals": {
    "flight": 220.00,
    "hotel": 331.70,
    "rail": 94.00,
    "trip_fee": 25.00,
    "agent_call_fee": 15.00,
    "grand_total": 685.70
  },
  "tax_rollup": [
    { "name": "Airport tax", "amount": 15.00 },
    { "name": "VAT", "rate": 0.07, "amount": 21.70 }
  ],
  "fee_rollup": [
    { "name": "YQ", "amount": 25.00 },
    { "name": "Booking fee", "amount": 4.00 }
  ],
  "transactions": [ ... ],
  "created_at": "2026-10-30T13:40:00Z",
  "updated_at": "2026-10-30T13:40:00Z"
}
```

#### Get Invoice by ID
```bash
curl -X GET http://localhost:8080/invoices/{invoice_id}
```

#### List Invoices (Paginated)
```bash
curl -X GET "http://localhost:8080/invoices?limit=50&offset=0"
```

#### Update Invoice (Rebuild Snapshot from Current Records)
```bash
curl -i -X PATCH http://localhost:8080/invoices/{invoice_id} \
  -H "Content-Type: application/json" \
  -d '{
    "transaction_ids": ["<new_tx_id_1>", "<new_tx_id_2>"]
  }'
```

#### Delete Invoice (Underlying Transactions Remain Intact)
```bash
curl -i -X DELETE http://localhost:8080/invoices/{invoice_id}
```
**Response:**
`204 No Content`. The invoice is removed; the included transactions still return `200 OK` via `GET /transactions/{id}`.
