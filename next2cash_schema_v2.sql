-- Next2Cash Schema v2 - Azure Compatible
-- No pgcrypto, no Greek text, ASCII only

-- 1. ENTITIES
CREATE TABLE IF NOT EXISTS entities (
    id              VARCHAR(50)  PRIMARY KEY,
    code            VARCHAR(20)  NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    icon            VARCHAR(50)  DEFAULT 'fa-building',
    color           VARCHAR(20)  DEFAULT '#2E75B6',
    currency        VARCHAR(10)  DEFAULT 'EUR',
    country         VARCHAR(10)  DEFAULT 'GR',
    is_active       BOOLEAN      DEFAULT TRUE,
    sort_order      INTEGER      DEFAULT 0,
    created_at      TIMESTAMPTZ  DEFAULT NOW()
);

INSERT INTO entities (id, code, name, icon, color, currency, sort_order) VALUES
    ('ent-next2me', 'next2me', 'Next2Me',  'fa-stethoscope', '#2E75B6', 'EUR', 1),
    ('ent-house',   'house',   'House',    'fa-home',         '#27ae60', 'EUR', 2),
    ('ent-polaris', 'polaris', 'Polaris',  'fa-ship',         '#8e44ad', 'EUR', 3)
ON CONFLICT (id) DO NOTHING;

-- 2. USERS
CREATE TABLE IF NOT EXISTS users (
    id              VARCHAR(50)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    email           VARCHAR(150),
    role            VARCHAR(20)  NOT NULL DEFAULT 'user'
                    CHECK (role IN ('admin', 'user', 'accountant', 'viewer')),
    is_active       BOOLEAN      DEFAULT TRUE,
    last_login      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  DEFAULT NOW()
);

-- 3. USER_ENTITIES
CREATE TABLE IF NOT EXISTS user_entities (
    user_id         VARCHAR(50) REFERENCES users(id)    ON DELETE CASCADE,
    entity_id       VARCHAR(50) REFERENCES entities(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, entity_id)
);

-- 4. CONFIG
CREATE TABLE IF NOT EXISTS config (
    id              VARCHAR(50)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
    entity_id       VARCHAR(50)  REFERENCES entities(id) ON DELETE CASCADE,
    config_type     VARCHAR(50)  NOT NULL,
    config_key      VARCHAR(200) NOT NULL,
    config_value    VARCHAR(200),
    parent_key      VARCHAR(200),
    icon            VARCHAR(50),
    sort_order      INTEGER DEFAULT 0,
    is_active       BOOLEAN DEFAULT TRUE,
    UNIQUE (entity_id, config_type, config_key)
);

CREATE INDEX IF NOT EXISTS idx_config_entity_type ON config(entity_id, config_type);

-- 5. BANK_ACCOUNTS
CREATE TABLE IF NOT EXISTS bank_accounts (
    id                  VARCHAR(50)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
    entity_id           VARCHAR(50)  REFERENCES entities(id) ON DELETE CASCADE,
    bank_name           VARCHAR(100) NOT NULL,
    account_label       VARCHAR(100),
    account_number      VARCHAR(50),
    iban                VARCHAR(34),
    account_type        VARCHAR(20) DEFAULT 'checking'
                        CHECK (account_type IN ('checking', 'savings', 'cash', 'credit')),
    currency            VARCHAR(10) DEFAULT 'EUR',
    current_balance     DECIMAL(15,2) DEFAULT 0.00,
    balance_date        DATE,
    linked_config_key   VARCHAR(200),
    is_active           BOOLEAN DEFAULT TRUE,
    sort_order          INTEGER DEFAULT 0,
    notes               TEXT,
    updated_by          VARCHAR(50) REFERENCES users(id),
    updated_at          TIMESTAMPTZ DEFAULT NOW(),
    created_at          TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bank_accounts_entity ON bank_accounts(entity_id);

-- 6. TRANSACTIONS
CREATE TABLE IF NOT EXISTS transactions (
    id                  SERIAL PRIMARY KEY,
    entity_id           VARCHAR(50)  NOT NULL REFERENCES entities(id),
    type                VARCHAR(10)  NOT NULL CHECK (type IN ('income', 'expense')),
    doc_date            DATE         NOT NULL,
    accounting_period   VARCHAR(20),
    counterparty        VARCHAR(200),
    account             VARCHAR(200),
    category            VARCHAR(200),
    subcategory         VARCHAR(200),
    description         TEXT,
    amount              DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    amount_paid         DECIMAL(15,2) DEFAULT 0.00,
    amount_remaining    DECIMAL(15,2) DEFAULT 0.00,
    payment_method      VARCHAR(100),
    payment_status      VARCHAR(20)  DEFAULT 'unpaid'
                        CHECK (payment_status IN ('unpaid', 'urgent', 'paid', 'received', 'partial')),
    payment_date        DATE,
    due_date            DATE,
    doc_status          VARCHAR(20)
                        CHECK (doc_status IN ('bank', 'receipt', 'cash', 'none')),
    blob_file_ids       TEXT,
    blob_folder_path    VARCHAR(500),
    record_status       VARCHAR(10)  DEFAULT 'active'
                        CHECK (record_status IN ('active', 'void')),
    approved            BOOLEAN DEFAULT FALSE,
    created_by          VARCHAR(50) REFERENCES users(id),
    updated_by          VARCHAR(50) REFERENCES users(id),
    created_at          TIMESTAMPTZ  DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_txn_entity         ON transactions(entity_id);
CREATE INDEX IF NOT EXISTS idx_txn_entity_date    ON transactions(entity_id, doc_date DESC);
CREATE INDEX IF NOT EXISTS idx_txn_type           ON transactions(entity_id, type);
CREATE INDEX IF NOT EXISTS idx_txn_payment_status ON transactions(entity_id, payment_status);
CREATE INDEX IF NOT EXISTS idx_txn_category       ON transactions(entity_id, category);
CREATE INDEX IF NOT EXISTS idx_txn_record_status  ON transactions(entity_id, record_status);
CREATE INDEX IF NOT EXISTS idx_txn_accounting     ON transactions(entity_id, accounting_period);

-- 7. PAYMENTS
CREATE TABLE IF NOT EXISTS payments (
    id                  SERIAL PRIMARY KEY,
    entity_id           VARCHAR(50)  NOT NULL REFERENCES entities(id),
    transaction_id      INTEGER REFERENCES transactions(id),
    payment_date        DATE NOT NULL,
    payment_period      VARCHAR(20),
    payment_type        VARCHAR(20) DEFAULT 'outgoing'
                        CHECK (payment_type IN ('outgoing', 'incoming')),
    amount              DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    payment_method      VARCHAR(100),
    bank_reference      VARCHAR(200),
    bank_account_id     VARCHAR(50) REFERENCES bank_accounts(id),
    counterparty        VARCHAR(200),
    description         TEXT,
    status              VARCHAR(20) DEFAULT 'completed'
                        CHECK (status IN ('completed', 'pending', 'cancelled')),
    blob_file_id        VARCHAR(500),
    notes               TEXT,
    created_by          VARCHAR(50) REFERENCES users(id),
    created_at          TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payments_entity ON payments(entity_id);
CREATE INDEX IF NOT EXISTS idx_payments_txn    ON payments(transaction_id);
CREATE INDEX IF NOT EXISTS idx_payments_date   ON payments(entity_id, payment_date DESC);

-- 8. DOCUMENTS
CREATE TABLE IF NOT EXISTS documents (
    id              VARCHAR(50)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
    entity_id       VARCHAR(50)  NOT NULL REFERENCES entities(id),
    transaction_id  INTEGER REFERENCES transactions(id) ON DELETE CASCADE,
    payment_id      INTEGER REFERENCES payments(id)     ON DELETE SET NULL,
    file_name       VARCHAR(500) NOT NULL,
    blob_path       VARCHAR(1000) NOT NULL,
    blob_folder     VARCHAR(500),
    mime_type       VARCHAR(100),
    file_size_bytes INTEGER,
    doc_type        VARCHAR(20) DEFAULT 'receipt'
                    CHECK (doc_type IN ('bank', 'receipt', 'cash', 'none')),
    doc_date        DATE,
    uploaded_by     VARCHAR(50) REFERENCES users(id),
    uploaded_at     TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_documents_entity ON documents(entity_id);
CREATE INDEX IF NOT EXISTS idx_documents_txn    ON documents(transaction_id);

-- 9. OBLIGATIONS
CREATE TABLE IF NOT EXISTS obligations (
    id              VARCHAR(50)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
    entity_id       VARCHAR(50)  NOT NULL REFERENCES entities(id),
    title           VARCHAR(300) NOT NULL,
    category        VARCHAR(200),
    subcategory     VARCHAR(200),
    counterparty    VARCHAR(200),
    amount          DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    due_date        DATE,
    frequency       VARCHAR(20) DEFAULT 'once'
                    CHECK (frequency IN ('once', 'monthly', 'quarterly', 'yearly')),
    status          VARCHAR(20) DEFAULT 'pending'
                    CHECK (status IN ('pending', 'paid', 'overdue', 'cancelled', 'urgent')),
    priority        VARCHAR(10) DEFAULT 'medium'
                    CHECK (priority IN ('high', 'medium', 'low')),
    payment_method  VARCHAR(100),
    linked_transaction_id INTEGER REFERENCES transactions(id),
    blob_file_id    VARCHAR(500),
    notes           TEXT,
    created_by      VARCHAR(50) REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_obligations_entity ON obligations(entity_id);
CREATE INDEX IF NOT EXISTS idx_obligations_status ON obligations(entity_id, status);
CREATE INDEX IF NOT EXISTS idx_obligations_due    ON obligations(entity_id, due_date);

-- 10. BUDGETS
CREATE TABLE IF NOT EXISTS budgets (
    id              VARCHAR(50)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
    entity_id       VARCHAR(50)  NOT NULL REFERENCES entities(id),
    year            INTEGER NOT NULL,
    month           INTEGER CHECK (month BETWEEN 1 AND 12),
    category        VARCHAR(200) NOT NULL,
    subcategory     VARCHAR(200),
    budgeted_amount DECIMAL(15,2) DEFAULT 0.00,
    notes           TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (entity_id, year, month, category, subcategory)
);

-- 11. AUDIT_LOG
CREATE TABLE IF NOT EXISTS audit_log (
    id              VARCHAR(50)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
    entity_id       VARCHAR(50)  REFERENCES entities(id),
    user_id         VARCHAR(50)  REFERENCES users(id),
    username        VARCHAR(50),
    action          VARCHAR(100) NOT NULL,
    target_table    VARCHAR(50),
    target_id       VARCHAR(100),
    details         JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_entity  ON audit_log(entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_user    ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at DESC);

-- TRIGGER FUNCTION: auto update_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_txn_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE TRIGGER trg_oblig_updated_at
    BEFORE UPDATE ON obligations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- TRIGGER FUNCTION: auto recalc balance after payment
CREATE OR REPLACE FUNCTION update_transaction_balance()
RETURNS TRIGGER AS $$
DECLARE
    v_txn_id INTEGER;
BEGIN
    IF TG_OP = 'DELETE' THEN
        v_txn_id = OLD.transaction_id;
    ELSE
        v_txn_id = NEW.transaction_id;
    END IF;

    IF v_txn_id IS NOT NULL THEN
        UPDATE transactions
        SET
            amount_paid      = COALESCE((SELECT SUM(amount) FROM payments WHERE transaction_id = v_txn_id), 0),
            amount_remaining = amount - COALESCE((SELECT SUM(amount) FROM payments WHERE transaction_id = v_txn_id), 0),
            updated_at       = NOW()
        WHERE id = v_txn_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_update_balance
    AFTER INSERT OR UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION update_transaction_balance();
