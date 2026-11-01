# Manual Testing Payloads & Curl Guide

This directory contains reusable JSON files to manually test every endpoint in the Navan Travel Commerce & Invoice Service.

---

## 1. Health Check
Spring Boot Actuator health: includes `db` and `diskSpace` components, returns `503` if any is `DOWN`.
```bash
curl -i http://localhost:8080/health
```

---

## 2. Transactions API (`/transactions`)

### Create Flight Transaction (201 Created)
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_flight.json
```

### Create Hotel Transaction (201 Created)
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_hotel.json
```

### Create Rail Transaction (201 Created)
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_rail.json
```

### Create Trip Fee Transaction (201 Created)
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_trip_fee.json
```

### Create Agent Call Fee Transaction (201 Created)
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_agent_call_fee.json
```

### Duplicate External ID (409 Conflict)
Posting with the same `external_id` returns **409 Conflict** along with the existing `id`:
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_duplicate.json
```

### Unknown Transaction Type (400 Bad Request)
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_invalid_type.json
```

### Unknown Navan Fee fee_kind (400 Bad Request)
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_invalid_fee_kind.json
```

### Empty Metadata `{}` (400 Bad Request)
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_empty_metadata.json
```

### Cross-Type Metadata / Storing Flight as Hotel Blob (400 Bad Request)
```bash
curl -i -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d @jsons/post_transaction_flight_as_hotel_blob.json
```

### Get Transaction by ID (200 OK / 404 Not Found)
```bash
# Replace <TXN_ID> with returned ID (e.g. txn_b421468c217b4630b73e1f7c066106ec)
curl -i http://localhost:8080/transactions/<TXN_ID>

# Non-existent ID -> 404 Not Found
curl -i http://localhost:8080/transactions/txn_nonexistent
```

### List Transactions (Pagination: 200 OK / 400 Bad Request)
```bash
# Default limit (50) and offset (0)
curl -i "http://localhost:8080/transactions"

# Custom limit and offset
curl -i "http://localhost:8080/transactions?limit=10&offset=5"

# Limit > 100 -> 400 Bad Request
curl -i "http://localhost:8080/transactions?limit=101"

# Limit < 1 -> 400 Bad Request
curl -i "http://localhost:8080/transactions?limit=0"

# Offset < 0 -> 400 Bad Request
curl -i "http://localhost:8080/transactions?offset=-1"
```

### Patch Transaction (200 OK / 404 / 409)
```bash
# Update unbilled transaction
curl -i -X PATCH http://localhost:8080/transactions/<TXN_ID> \
  -H "Content-Type: application/json" \
  -d @jsons/patch_transaction.json

# Non-existent ID -> 404 Not Found
curl -i -X PATCH http://localhost:8080/transactions/txn_nonexistent \
  -H "Content-Type: application/json" \
  -d @jsons/patch_transaction.json

# If transaction is already invoiced -> 409 Conflict (Invoiced Freeze)
```

### Delete Transaction (204 No Content / 404 / 409)
```bash
# Delete unbilled transaction -> 204 No Content
curl -i -X DELETE http://localhost:8080/transactions/<TXN_ID>

# Non-existent ID -> 404 Not Found
curl -i -X DELETE http://localhost:8080/transactions/txn_nonexistent

# If transaction is already invoiced -> 409 Conflict (Invoiced Freeze)
```

---

## 3. Invoices API (`/invoices`)

### Create Single-Transaction Invoice (201 Created)
Edit `jsons/post_invoice_single.json` with your real `<TXN_ID>`, then:
```bash
curl -i -X POST http://localhost:8080/invoices \
  -H "Content-Type: application/json" \
  -d '{"transaction_id": "<TXN_ID>"}'
```

### Create Multi-Transaction Trip Invoice (201 Created)
```bash
curl -i -X POST http://localhost:8080/invoices \
  -H "Content-Type: application/json" \
  -d '{"transaction_ids": ["<TXN_FLIGHT_ID>", "<TXN_HOTEL_ID>", "<TXN_RAIL_ID>", "<TXN_TRIP_FEE_ID>", "<TXN_AGENT_FEE_ID>"]}'
```

### Create Invoice - Empty Transaction IDs (400 Bad Request)
```bash
curl -i -X POST http://localhost:8080/invoices \
  -H "Content-Type: application/json" \
  -d @jsons/post_invoice_empty.json
```

### Create Invoice - Duplicate Transaction ID in List (409 Conflict)
```bash
curl -i -X POST http://localhost:8080/invoices \
  -H "Content-Type: application/json" \
  -d '{"transaction_ids": ["<TXN_ID>", "<TXN_ID>"]}'
```

### Create Invoice - Mixed Currency EUR + USD (409 Conflict)
```bash
curl -i -X POST http://localhost:8080/invoices \
  -H "Content-Type: application/json" \
  -d '{"transaction_ids": ["<EUR_TXN_ID>", "<USD_TXN_ID>"]}'
```

### Get Invoice by ID (200 OK / 404 Not Found)
```bash
curl -i http://localhost:8080/invoices/<INVOICE_ID>

# Non-existent ID -> 404 Not Found
curl -i http://localhost:8080/invoices/inv_nonexistent
```

### List Invoices (Pagination: 200 OK / 400 Bad Request)
```bash
# Default
curl -i "http://localhost:8080/invoices"

# Custom limit and offset
curl -i "http://localhost:8080/invoices?limit=10&offset=0"

# Invalid bounds -> 400 Bad Request
curl -i "http://localhost:8080/invoices?limit=101"
```

### Patch Invoice (200 OK / 404 / 409 / 400)
Replaces the invoiced transactions and recalculates totals and rollups:
```bash
curl -i -X PATCH http://localhost:8080/invoices/<INVOICE_ID> \
  -H "Content-Type: application/json" \
  -d '{"transaction_ids": ["<TXN_ID_1>", "<TXN_ID_2>"]}'
```

### Delete Invoice (204 No Content / 404 Not Found)
Deletes the invoice and its snapshot rows, leaving underlying transactions intact:
```bash
curl -i -X DELETE http://localhost:8080/invoices/<INVOICE_ID>

# Non-existent ID -> 404 Not Found
curl -i -X DELETE http://localhost:8080/invoices/inv_nonexistent
```
