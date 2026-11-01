# Navan Travel Commerce & Invoice Service

A production-grade microservice built with **Java 21**, **Spring Boot 3.3.5**, and **SQLite** for handling structured travel transactions (`flight`, `hotel`, `rail`, `navan_fee`), duplicate detection under concurrency, immutable invoice snapshots, and rollups.

---

## Quickstart

### Prerequisites
- JDK 21 (Eclipse Adoptium / OpenJDK)
- macOS / Linux / Windows

### 1. Run the API (One Command)
```bash
./gradlew bootRun
```
The application will start on `http://localhost:8080` backed by the SQLite database `./data/travel_invoice.db`.

### 2. Run All Automated Tests & Check Coverage
```bash
./gradlew test jacocoTestReport
```
Executes the comprehensive 32-test suite covering every endpoint, failure scenario, concurrency race, and freeze rule.
- **Coverage report:** `build/reports/jacoco/test/html/index.html` (Overall: **87.91% line coverage**, **89.26% instruction coverage**).
- **Enforce 80% rule:**
```bash
./gradlew jacocoTestCoverageVerification
```

### 3. Manual Testing via `jsons/` Directory
The `jsons/` directory contains ready-to-use payloads for testing all endpoints and failure scenarios:
- `post_transaction_flight.json`, `post_transaction_hotel.json`, `post_transaction_rail.json`, `post_transaction_trip_fee.json`, `post_transaction_agent_call_fee.json`
- `post_transaction_duplicate.json` (409 Conflict)
- `post_transaction_invalid_type.json`, `post_transaction_invalid_fee_kind.json`, `post_transaction_empty_metadata.json`, `post_transaction_flight_as_hotel_blob.json` (400 Bad Request)
- `patch_transaction.json`, `post_invoice_single.json`, `post_invoice_trip.json`, `post_invoice_mixed_currency.json`, `post_invoice_duplicate_id.json`, `post_invoice_empty.json`, `patch_invoice.json`
See [`jsons/README.md`](file:///Users/miha/Projects/NavanTestTask/jsons/README.md) for curl command examples using these payloads.

---

## Example `curl` Commands

### 1. Health Check
```bash
curl -X GET http://localhost:8080/health
```

---

### 2. Create Transactions (Single Endpoint `POST /transactions`)

#### Flight
```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "type": "flight",
    "external_id": "flight-TXL-LHR-2026-03-11",
    "currency": "EUR",
    "occurred_at": "2026-03-11T06:40:00Z",
    "total": 220.0,
    "tax_lines": [{ "name": "Airport tax", "amount": 15.0 }],
    "fee_lines": [{ "name": "YQ", "amount": 25.0 }],
    "metadata": {
      "origin": "TXL",
      "destination": "LHR",
      "fare": 180.0,
      "coupons": [{
        "carrier": "BA",
        "flight_number": "BA982",
        "from": "TXL",
        "to": "LHR",
        "departure": "2026-03-11T07:00:00Z",
        "arrival": "2026-03-11T08:20:00Z"
      }]
    }
  }'
```

#### Hotel
```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "type": "hotel",
    "external_id": "hotel-LON-2026-03-11",
    "currency": "EUR",
    "occurred_at": "2026-03-11T15:00:00Z",
    "total": 331.7,
    "tax_lines": [{ "name": "VAT", "rate": 0.07, "amount": 21.7 }],
    "fee_lines": [],
    "metadata": {
      "name": "Hotel Mitte London",
      "address": {
        "street": "12 Kingsway",
        "city": "London",
        "country": "GB"
      },
      "check_in": "2026-03-11",
      "check_out": "2026-03-13",
      "nights": 2,
      "room_rate_per_night": 140.0,
      "extras": [{ "name": "Breakfast", "amount": 30.0 }]
    }
  }'
```

#### Rail
```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "type": "rail",
    "external_id": "rail-STP-PAR-2026-03-13",
    "currency": "EUR",
    "occurred_at": "2026-03-13T09:00:00Z",
    "total": 94.0,
    "tax_lines": [],
    "fee_lines": [{ "name": "Booking fee", "amount": 4.0 }],
    "metadata": {
      "legs": [
        {
          "origin": "London St Pancras",
          "destination": "Lille Europe",
          "departure": "2026-03-13T09:00:00Z",
          "arrival": "2026-03-13T10:30:00Z",
          "train_number": "EST9108",
          "class": "standard"
        },
        {
          "origin": "Lille Europe",
          "destination": "Paris Nord",
          "departure": "2026-03-13T11:00:00Z",
          "arrival": "2026-03-13T12:05:00Z",
          "train_number": "EST9122",
          "class": "standard"
        }
      ]
    }
  }'
```

#### Navan Fee (Trip Fee)
```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "type": "navan_fee",
    "external_id": "navan-trip-fee-2026-03-11",
    "currency": "EUR",
    "occurred_at": "2026-03-11T06:40:00Z",
    "total": 25.0,
    "tax_lines": [],
    "fee_lines": [],
    "metadata": {
      "fee_kind": "trip_fee",
      "description": "Navan trip fee",
      "related_external_id": "flight-TXL-LHR-2026-03-11"
    }
  }'
```

#### Navan Fee (Agent Call Fee)
```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "type": "navan_fee",
    "external_id": "navan-agent-call-2026-03-11",
    "currency": "EUR",
    "occurred_at": "2026-03-11T18:00:00Z",
    "total": 15.0,
    "tax_lines": [],
    "fee_lines": [],
    "metadata": {
      "fee_kind": "agent_call_fee",
      "description": "Agent fee for support call",
      "related_external_id": "hotel-LON-2026-03-11"
    }
  }'
```

#### Unknown Transaction Type (Returns 400 Bad Request)
Unrecognized types map to `TransactionType.UNSUPPORTED` and are rejected:
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "type": "spaceship",
    "external_id": "ship-001",
    "currency": "EUR",
    "occurred_at": "2026-03-11T18:00:00Z",
    "total": 100.0,
    "tax_lines": [],
    "fee_lines": [],
    "metadata": { "detail": "warp drive" }
  }'
```
**Response:**
```json
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "Unknown transaction type: spaceship"
}
```

---

### 3. Duplicate Transaction Create (Returns 409 Conflict with Existing ID)
Posting the same `external_id` a second time rejects duplicate persistence and returns `409 Conflict`:
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "type": "flight",
    "external_id": "flight-TXL-LHR-2026-03-11",
    "currency": "EUR",
    "occurred_at": "2026-03-11T06:40:00Z",
    "total": 220.0,
    "tax_lines": [],
    "fee_lines": [],
    "metadata": {
      "origin": "TXL",
      "destination": "LHR",
      "fare": 220.0,
      "coupons": []
    }
  }'
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
curl -X PATCH http://localhost:8080/transactions/{transaction_id} \
  -H "Content-Type: application/json" \
  -d '{
    "total": 240.0
  }'
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
curl -X POST http://localhost:8080/invoices \
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
**Response:**
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
curl -X PATCH http://localhost:8080/invoices/{invoice_id} \
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
