-- Navan Travel Commerce & Invoice Service - Reference Relational Schema (SQLite DDL)

CREATE TABLE IF NOT EXISTS transactions (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    external_id VARCHAR(128) NOT NULL UNIQUE,
    currency VARCHAR(3) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    total NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT
);

CREATE INDEX IF NOT EXISTS idx_tx_external_id ON transactions (external_id);
CREATE INDEX IF NOT EXISTS idx_tx_type ON transactions (type);
CREATE INDEX IF NOT EXISTS idx_tx_occurred_at ON transactions (occurred_at);

CREATE TABLE IF NOT EXISTS transaction_tax_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    rate NUMERIC(7, 4),
    amount NUMERIC(19, 2) NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tax_transaction_id ON transaction_tax_lines (transaction_id);

CREATE TABLE IF NOT EXISTS transaction_fee_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_fee_transaction_id ON transaction_fee_lines (transaction_id);

CREATE TABLE IF NOT EXISTS flight_metadata (
    transaction_id VARCHAR(64) PRIMARY KEY,
    origin VARCHAR(64) NOT NULL,
    destination VARCHAR(64) NOT NULL,
    fare NUMERIC(19, 2) NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS flight_coupons (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    flight_metadata_id VARCHAR(64) NOT NULL,
    carrier VARCHAR(16) NOT NULL,
    flight_number VARCHAR(32) NOT NULL,
    from_location VARCHAR(64) NOT NULL,
    to_location VARCHAR(64) NOT NULL,
    departure VARCHAR(64) NOT NULL,
    arrival VARCHAR(64) NOT NULL,
    coupon_order INTEGER NOT NULL,
    FOREIGN KEY (flight_metadata_id) REFERENCES flight_metadata (transaction_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS hotel_metadata (
    transaction_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    street VARCHAR(255) NOT NULL,
    city VARCHAR(128) NOT NULL,
    country VARCHAR(64) NOT NULL,
    check_in VARCHAR(32) NOT NULL,
    check_out VARCHAR(32) NOT NULL,
    nights INTEGER NOT NULL,
    room_rate_per_night NUMERIC(19, 2) NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS hotel_extras (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    hotel_metadata_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    FOREIGN KEY (hotel_metadata_id) REFERENCES hotel_metadata (transaction_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS rail_metadata (
    transaction_id VARCHAR(64) PRIMARY KEY,
    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS rail_legs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    rail_metadata_id VARCHAR(64) NOT NULL,
    origin VARCHAR(128) NOT NULL,
    destination VARCHAR(128) NOT NULL,
    departure VARCHAR(64) NOT NULL,
    arrival VARCHAR(64) NOT NULL,
    train_number VARCHAR(64),
    travel_class VARCHAR(64),
    leg_order INTEGER NOT NULL,
    FOREIGN KEY (rail_metadata_id) REFERENCES rail_metadata (transaction_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS navan_fee_metadata (
    transaction_id VARCHAR(64) PRIMARY KEY,
    fee_kind VARCHAR(64) NOT NULL,
    description TEXT,
    related_external_id VARCHAR(128),
    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_fee_kind ON navan_fee_metadata (fee_kind);
CREATE INDEX IF NOT EXISTS idx_fee_related_ext_id ON navan_fee_metadata (related_external_id);

CREATE TABLE IF NOT EXISTS invoices (
    id VARCHAR(64) PRIMARY KEY,
    currency VARCHAR(3) NOT NULL,
    grand_total NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_invoice_created_at ON invoices (created_at);

CREATE TABLE IF NOT EXISTS invoice_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_id VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(64) NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    occurred_at VARCHAR(64) NOT NULL,
    total NUMERIC(19, 2) NOT NULL,
    tax_lines_json TEXT NOT NULL,
    fee_lines_json TEXT NOT NULL,
    metadata_json TEXT NOT NULL,
    FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_inv_line_invoice_id ON invoice_lines (invoice_id);
CREATE INDEX IF NOT EXISTS idx_inv_line_transaction_id ON invoice_lines (transaction_id);
