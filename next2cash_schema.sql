-- ============================================================
-- NEXT2CASH — PostgreSQL 15 Schema
-- Next2me Group | ACashControl → Next2Cash
-- Version: 1.0 | April 2026
-- ============================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- 1. ENTITIES (Εταιρείες Ομίλου)
-- ============================================================
CREATE TABLE entities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(20)  NOT NULL UNIQUE,   -- 'next2me', 'house', 'polaris'
    name            VARCHAR(100) NOT NULL,           -- 'Next2Me', 'House', 'Polaris'
    icon            VARCHAR(50)  DEFAULT 'fa-building',
    color           VARCHAR(20)  DEFAULT '#2E75B6',
    currency        VARCHAR(10)  DEFAULT 'EUR',
    country         VARCHAR(10)  DEFAULT 'GR',
    is_active       BOOLEAN      DEFAULT TRUE,
    sort_order      INTEGER      DEFAULT 0,
    created_at      TIMESTAMPTZ  DEFAULT NOW()
);

-- Seed data
INSERT INTO entities (code, name, icon, color, currency, sort_order) VALUES
    ('next2me', 'Next2Me',  'fa-stethoscope', '#2E75B6', 'EUR', 1),
    ('house',   'House',    'fa-home',         '#27ae60', 'EUR', 2),
    ('polaris', 'Polaris',  'fa-ship',         '#8e44ad', 'EUR', 3);


-- ============================================================
-- 2. USERS (Χρήστες)
-- ============================================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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


-- ============================================================
-- 3. USER_ENTITIES (Ποιος χρήστης έχει πρόσβαση σε ποια entity)
-- ============================================================
CREATE TABLE user_entities (
    user_id         UUID REFERENCES users(id)    ON DELETE CASCADE,
    entity_id       UUID REFERENCES entities(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, entity_id)
);


-- ============================================================
-- 4. CONFIG (Master Data: categories, subcategories, payment_methods, settings)
-- Ακριβώς όπως το legacy Config sheet
-- ============================================================
CREATE TABLE config (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id       UUID REFERENCES entities(id) ON DELETE CASCADE,
    config_type     VARCHAR(50)  NOT NULL,   -- 'category', 'subcategory', 'payment_method', 'setting', 'card'
    config_key      VARCHAR(200) NOT NULL,
    config_value    VARCHAR(200),
    parent_key      VARCHAR(200),            -- για subcategory: το category key
    icon            VARCHAR(50),             -- για cards
    sort_order      INTEGER DEFAULT 0,
    is_active       BOOLEAN DEFAULT TRUE,
    UNIQUE (entity_id, config_type, config_key)
);

-- Index για γρήγορη αναζήτηση ανά entity + type
CREATE INDEX idx_config_entity_type ON config(entity_id, config_type);


-- ============================================================
-- 5. BANK_ACCOUNTS (Τραπεζικοί Λογαριασμοί)
-- Χειροκίνητα ενημερωμένα υπόλοιπα — ακριβώς ως legacy
-- ============================================================
CREATE TABLE bank_accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id           UUID REFERENCES entities(id) ON DELETE CASCADE,
    bank_name           VARCHAR(100) NOT NULL,       -- 'Πειραιώς', 'Viva Wallet', 'Μετρητά'
    account_label       VARCHAR(100),
    account_number      VARCHAR(50),
    iban                VARCHAR(34),
    account_type        VARCHAR(20) DEFAULT 'checking'
                        CHECK (account_type IN ('checking', 'savings', 'cash', 'credit')),
    currency            VARCHAR(10) DEFAULT 'EUR',
    current_balance     DECIMAL(15,2) DEFAULT 0.00,
    balance_date        DATE,
    linked_config_key   VARCHAR(200),               -- FK → config.config_key (payment_method)
    is_active           BOOLEAN DEFAULT TRUE,
    sort_order          INTEGER DEFAULT 0,
    notes               TEXT,
    updated_by          UUID REFERENCES users(id),
    updated_at          TIMESTAMPTZ DEFAULT NOW(),
    created_at          TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_bank_accounts_entity ON bank_accounts(entity_id);


-- ============================================================
-- 6. TRANSACTIONS (Κύριος πίνακας κινήσεων)
-- Κάθε γραμμή = 1 οικονομική κίνηση
-- ============================================================
CREATE TABLE transactions (
    id                  SERIAL PRIMARY KEY,          -- INTEGER auto-increment (ΟΧΙ UUID — legacy compatibility)
    entity_id           UUID NOT NULL REFERENCES entities(id),

    -- Τύπος & Ημερομηνίες
    type                VARCHAR(10)  NOT NULL CHECK (type IN ('income', 'expense')),
    doc_date            DATE         NOT NULL,
    accounting_period   VARCHAR(20),                 -- YYYY-MM format

    -- Περιγραφή
    counterparty        VARCHAR(200),                -- Αντισυμβαλλόμενος
    account             VARCHAR(200),                -- Λογαριασμός (FK → config subcategory key)
    category            VARCHAR(200),                -- Κατηγορία (FK → config category key)
    subcategory         VARCHAR(200),                -- Υποκατηγορία
    description         TEXT,                        -- Περιγραφή κίνησης (ΟΧΙ comments)

    -- Ποσά — ΕΝΑ πεδίο amount (ΟΧΙ inflow/outflow)
    amount              DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    amount_paid         DECIMAL(15,2) DEFAULT 0.00,
    amount_remaining    DECIMAL(15,2) DEFAULT 0.00,

    -- Πληρωμή
    payment_method      VARCHAR(100),                -- FK → config payment_method key
    payment_status      VARCHAR(20)  DEFAULT 'unpaid'
                        CHECK (payment_status IN ('unpaid', 'urgent', 'paid', 'received', 'partial')),
    payment_date        DATE,
    due_date            DATE,

    -- Παραστατικό (doc_status)
    doc_status          VARCHAR(20)
                        CHECK (doc_status IN ('bank', 'receipt', 'cash', 'none', NULL)),

    -- Azure Blob Storage (αντικαθιστά Google Drive)
    -- Αποθηκεύουμε τα blob paths comma-separated (ίδια λογική με legacy drive_file_ids)
    blob_file_ids       TEXT,                        -- 'blob-path-1,blob-path-2,...'
    blob_folder_path    VARCHAR(500),                -- entity_id/year/month/txn_id/

    -- Audit
    record_status       VARCHAR(10)  DEFAULT 'active'
                        CHECK (record_status IN ('active', 'void')),
    approved            BOOLEAN DEFAULT FALSE,
    created_by          UUID REFERENCES users(id),
    updated_by          UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ  DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  DEFAULT NOW()
);

-- Indexes για γρήγορες queries
CREATE INDEX idx_txn_entity          ON transactions(entity_id);
CREATE INDEX idx_txn_entity_date     ON transactions(entity_id, doc_date DESC);
CREATE INDEX idx_txn_type            ON transactions(entity_id, type);
CREATE INDEX idx_txn_payment_status  ON transactions(entity_id, payment_status);
CREATE INDEX idx_txn_category        ON transactions(entity_id, category);
CREATE INDEX idx_txn_record_status   ON transactions(entity_id, record_status);
CREATE INDEX idx_txn_accounting      ON transactions(entity_id, accounting_period);


-- ============================================================
-- 7. PAYMENTS (Πληρωμές που εξοφλούν Transactions)
-- ============================================================
CREATE TABLE payments (
    id                  SERIAL PRIMARY KEY,
    entity_id           UUID NOT NULL REFERENCES entities(id),
    transaction_id      INTEGER REFERENCES transactions(id),
    payment_date        DATE NOT NULL,
    payment_period      VARCHAR(20),
    payment_type        VARCHAR(20) DEFAULT 'outgoing'
                        CHECK (payment_type IN ('outgoing', 'incoming')),
    amount              DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    payment_method      VARCHAR(100),
    bank_reference      VARCHAR(200),
    bank_account_id     UUID REFERENCES bank_accounts(id),
    counterparty        VARCHAR(200),
    description         TEXT,
    status              VARCHAR(20) DEFAULT 'completed'
                        CHECK (status IN ('completed', 'pending', 'cancelled')),
    -- Azure Blob (προαιρετικά αποδεικτικό πληρωμής)
    blob_file_id        VARCHAR(500),
    notes               TEXT,
    created_by          UUID REFERENCES users(id),
    created_at          TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_payments_entity     ON payments(entity_id);
CREATE INDEX idx_payments_txn        ON payments(transaction_id);
CREATE INDEX idx_payments_date       ON payments(entity_id, payment_date DESC);


-- ============================================================
-- 8. DOCUMENTS (Παραστατικά → Azure Blob Storage)
-- Κάθε transaction μπορεί να έχει πολλά αρχεία
-- ============================================================
CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id       UUID NOT NULL REFERENCES entities(id),
    transaction_id  INTEGER REFERENCES transactions(id) ON DELETE CASCADE,
    payment_id      INTEGER REFERENCES payments(id)     ON DELETE SET NULL,

    -- File info
    file_name       VARCHAR(500) NOT NULL,           -- '4778 - ΦΡΟΝΤΙΣΤΗΡΙΟ ΣΠΥΡΟΣ.pdf'
    blob_path       VARCHAR(1000) NOT NULL,          -- Azure Blob path
    blob_folder     VARCHAR(500),                    -- entity_id/year/month/
    mime_type       VARCHAR(100),
    file_size_bytes INTEGER,

    -- Τύπος παραστατικού (doc_status)
    doc_type        VARCHAR(20) DEFAULT 'receipt'
                    CHECK (doc_type IN ('bank', 'receipt', 'cash', 'none')),

    -- Metadata
    doc_date        DATE,
    uploaded_by     UUID REFERENCES users(id),
    uploaded_at     TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_documents_entity  ON documents(entity_id);
CREATE INDEX idx_documents_txn     ON documents(transaction_id);


-- ============================================================
-- 9. OBLIGATIONS (Υποχρεώσεις)
-- Standalone — ΔΕΝ derive από transactions (separate CRUD)
-- ============================================================
CREATE TABLE obligations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id       UUID NOT NULL REFERENCES entities(id),
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
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_obligations_entity  ON obligations(entity_id);
CREATE INDEX idx_obligations_status  ON obligations(entity_id, status);
CREATE INDEX idx_obligations_due     ON obligations(entity_id, due_date);


-- ============================================================
-- 10. BUDGETS (Προϋπολογισμός — Phase 2)
-- ============================================================
CREATE TABLE budgets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id       UUID NOT NULL REFERENCES entities(id),
    year            INTEGER NOT NULL,
    month           INTEGER CHECK (month BETWEEN 1 AND 12),  -- NULL = ετήσιο
    category        VARCHAR(200) NOT NULL,
    subcategory     VARCHAR(200),
    budgeted_amount DECIMAL(15,2) DEFAULT 0.00,
    notes           TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (entity_id, year, month, category, subcategory)
);


-- ============================================================
-- 11. AUDIT_LOG (Καταγραφή ενεργειών)
-- ============================================================
CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id       UUID REFERENCES entities(id),
    user_id         UUID REFERENCES users(id),
    username        VARCHAR(50),
    action          VARCHAR(100) NOT NULL,   -- 'CREATE_TRANSACTION', 'VOID_TRANSACTION', etc.
    target_table    VARCHAR(50),
    target_id       VARCHAR(100),
    details         JSONB,                   -- Πλήρη λεπτομέρεια αλλαγής
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_audit_entity    ON audit_log(entity_id);
CREATE INDEX idx_audit_user      ON audit_log(user_id);
CREATE INDEX idx_audit_created   ON audit_log(created_at DESC);


-- ============================================================
-- SEED DATA — Categories για NEXT2ME entity
-- ============================================================
-- (θα συμπληρωθεί με migration script από ATLAS_Financial_Database.xlsx)

-- ============================================================
-- SEED DATA — Categories για HOUSE entity
-- ============================================================
-- (θα συμπληρωθεί με migration script από CashControl___House.xlsx)


-- ============================================================
-- VIEWS — Χρήσιμα για Dashboard KPIs
-- ============================================================

-- View: Transactions με entity info (για queries χωρίς JOIN)
CREATE VIEW v_transactions AS
SELECT
    t.*,
    e.code  AS entity_code,
    e.name  AS entity_name,
    e.currency
FROM transactions t
JOIN entities e ON t.entity_id = e.id
WHERE t.record_status = 'active';

-- View: Dashboard KPIs ανά entity + period
CREATE VIEW v_dashboard_kpis AS
SELECT
    entity_id,
    DATE_TRUNC('month', doc_date) AS month,
    SUM(CASE WHEN type = 'income'  THEN amount ELSE 0 END) AS total_income,
    SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END) AS total_expense,
    SUM(CASE WHEN type = 'income'  THEN amount ELSE 0 END) -
    SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END) AS net,
    SUM(CASE WHEN payment_status IN ('unpaid','urgent') AND type='expense' THEN amount_remaining ELSE 0 END) AS total_unpaid,
    SUM(CASE WHEN payment_status = 'urgent' AND type='expense' THEN amount_remaining ELSE 0 END) AS total_urgent,
    COUNT(*) AS transaction_count
FROM transactions
WHERE record_status = 'active'
GROUP BY entity_id, DATE_TRUNC('month', doc_date);


-- ============================================================
-- FUNCTIONS
-- ============================================================

-- Function: Υπολογισμός amount_remaining μετά από κάθε payment
CREATE OR REPLACE FUNCTION update_transaction_balance()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE transactions
    SET
        amount_paid      = (SELECT COALESCE(SUM(amount), 0) FROM payments WHERE transaction_id = NEW.transaction_id),
        amount_remaining = amount - (SELECT COALESCE(SUM(amount), 0) FROM payments WHERE transaction_id = NEW.transaction_id),
        updated_at       = NOW()
    WHERE id = NEW.transaction_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: Αυτόματη ενημέρωση balance μετά από κάθε payment
CREATE TRIGGER trg_update_balance
    AFTER INSERT OR UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION update_transaction_balance();

-- Function: updated_at auto-update
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_txn_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_oblig_updated_at
    BEFORE UPDATE ON obligations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
