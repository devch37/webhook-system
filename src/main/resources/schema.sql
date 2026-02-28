PRAGMA foreign_keys=ON;

DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS webhook_events;

CREATE TABLE accounts (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    account_key  TEXT    NOT NULL UNIQUE,
    email        TEXT,
    status       TEXT    NOT NULL DEFAULT 'ACTIVE'
                         CHECK(status IN ('ACTIVE','DELETED','APPLE_DELETED')),
    created_at   TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE webhook_events (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id      TEXT    NOT NULL UNIQUE,
    event_type    TEXT    NOT NULL,
    payload       TEXT    NOT NULL,
    status        TEXT    NOT NULL DEFAULT 'RECEIVED'
                          CHECK(status IN ('RECEIVED','PROCESSING','DONE','FAILED')),
    error_message TEXT,
    created_at    TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_webhook_events_status ON webhook_events(status);
CREATE INDEX IF NOT EXISTS idx_accounts_key ON accounts(account_key);
