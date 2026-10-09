-- SQLite schema. Dates are stored as local YYYY-MM-DD HH:MM:SS text.
CREATE TABLE IF NOT EXISTS users (
 user_id INTEGER PRIMARY KEY AUTOINCREMENT,
 full_name TEXT NOT NULL,
 email TEXT NOT NULL UNIQUE,
 password_hash TEXT NOT NULL,
 role TEXT NOT NULL CHECK (role IN ('CUSTOMER','PROVIDER'))
);
CREATE TABLE IF NOT EXISTS providers (
 provider_id INTEGER PRIMARY KEY AUTOINCREMENT,
 user_id INTEGER NOT NULL UNIQUE REFERENCES users(user_id),
 bio TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS services (
 service_id INTEGER PRIMARY KEY AUTOINCREMENT,
 name TEXT NOT NULL UNIQUE,
 description TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS availability_slots (
 slot_id INTEGER PRIMARY KEY AUTOINCREMENT,
 provider_id INTEGER NOT NULL REFERENCES providers(provider_id),
 service_id INTEGER NOT NULL REFERENCES services(service_id),
 starts_at TEXT NOT NULL,
 ends_at TEXT NOT NULL,
 version INTEGER NOT NULL DEFAULT 0,
 CONSTRAINT uq_provider_start UNIQUE(provider_id, starts_at),
 CONSTRAINT ck_valid_start CHECK (datetime(starts_at) IS NOT NULL),
 CONSTRAINT ck_whole_hour CHECK (strftime('%M:%S', starts_at) = '00:00'),
 CONSTRAINT ck_one_hour CHECK (datetime(ends_at) = datetime(starts_at, '+1 hour'))
);
CREATE TABLE IF NOT EXISTS appointments (
 appointment_id INTEGER PRIMARY KEY AUTOINCREMENT,
 customer_id INTEGER NOT NULL REFERENCES users(user_id),
 slot_id INTEGER NOT NULL REFERENCES availability_slots(slot_id),
 status TEXT NOT NULL CHECK(status IN ('BOOKED','CANCELLED','COMPLETED')),
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Only one BOOKED row may reference a slot. Cancelled rows remain as history.
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_booking ON appointments(slot_id) WHERE status='BOOKED';
CREATE TABLE IF NOT EXISTS demo_seed_state (
 id INTEGER PRIMARY KEY CHECK (id = 1)
);
