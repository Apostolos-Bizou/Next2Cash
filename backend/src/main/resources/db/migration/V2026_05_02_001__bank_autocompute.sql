-- =====================================================================
-- Phase 1: Bank balance auto-compute infrastructure (FIXED v3)
-- Date: 2026-05-02
-- Adds 'virtual' to account_type check constraint before INSERT
-- =====================================================================

BEGIN;

-- 1.0: Show existing check constraint (for diagnostics)
SELECT 
    conname AS constraint_name,
    pg_get_constraintdef(oid) AS definition
FROM pg_constraint 
WHERE conrelid = 'bank_accounts'::regclass 
  AND conname LIKE '%account_type%';

-- 1.0b: Drop old constraint and recreate with 'virtual' allowed
ALTER TABLE bank_accounts DROP CONSTRAINT IF EXISTS bank_accounts_account_type_check;
ALTER TABLE bank_accounts ADD CONSTRAINT bank_accounts_account_type_check 
    CHECK (account_type IN ('checking', 'savings', 'cash', 'revolut', 'virtual'));

-- 1.1: Add new columns to bank_accounts
ALTER TABLE bank_accounts 
    ADD COLUMN IF NOT EXISTS opening_balance NUMERIC(15,2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS opening_date DATE,
    ADD COLUMN IF NOT EXISTS last_recomputed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS fx_rate_to_eur NUMERIC(10,6) DEFAULT 1.000000,
    ADD COLUMN IF NOT EXISTS is_virtual BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN bank_accounts.opening_balance IS 'Anchor balance at opening_date. Auto-computed balance = opening + sum(transactions after opening_date)';
COMMENT ON COLUMN bank_accounts.opening_date IS 'Date from which transactions are considered for auto-compute';
COMMENT ON COLUMN bank_accounts.last_recomputed_at IS 'Last successful auto-recompute run timestamp';
COMMENT ON COLUMN bank_accounts.fx_rate_to_eur IS 'Manual FX rate for conversion to EUR (1.0 for EUR accounts)';
COMMENT ON COLUMN bank_accounts.is_virtual IS 'TRUE for system-managed virtual accounts like Ανεκχώρητο';

-- 1.2: Migration of existing accounts
UPDATE bank_accounts 
SET 
    opening_balance = current_balance,
    opening_date = COALESCE(balance_date, DATE '2026-04-30'),
    last_recomputed_at = NULL,
    fx_rate_to_eur = CASE 
        WHEN currency = 'EUR' THEN 1.000000
        WHEN currency = 'GBP' THEN 1.170000
        WHEN currency = 'USD' THEN 0.920000
        ELSE 1.000000
    END
WHERE opening_balance = 0.00 OR opening_balance IS NULL OR opening_date IS NULL;

-- 1.3: Create "Ανεκχώρητο" virtual account for each entity that has bank_accounts
INSERT INTO bank_accounts (
    id, entity_id, bank_name, account_label, account_type, 
    currency, current_balance, opening_balance, opening_date,
    balance_date, is_active, is_virtual, sort_order, fx_rate_to_eur,
    created_at, updated_at
)
SELECT DISTINCT
    gen_random_uuid(),
    ba.entity_id,
    'Σύστημα'::text,
    'Ανεκχώρητο'::text,
    'virtual'::text,
    'EUR'::text,
    0.00,
    0.00,
    DATE '2026-04-30',
    CURRENT_DATE,
    TRUE,
    TRUE,
    99,
    1.000000,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM bank_accounts ba
WHERE NOT EXISTS (
    SELECT 1 FROM bank_accounts ba2 
    WHERE ba2.entity_id = ba.entity_id 
    AND ba2.account_label = 'Ανεκχώρητο'
);

-- 1.4: Verification queries
SELECT 
    e.name AS entity, 
    COUNT(*) AS total_accounts,
    COUNT(*) FILTER (WHERE is_virtual = TRUE) AS virtual_accounts,
    COUNT(*) FILTER (WHERE is_virtual = FALSE OR is_virtual IS NULL) AS real_accounts
FROM bank_accounts ba
JOIN entities e ON e.id = ba.entity_id
GROUP BY e.name
ORDER BY e.name;

SELECT 
    e.name AS entity,
    ba.account_label,
    ba.currency,
    ba.current_balance,
    ba.opening_balance,
    ba.opening_date,
    ba.fx_rate_to_eur,
    ba.is_virtual
FROM bank_accounts ba
JOIN entities e ON e.id = ba.entity_id
ORDER BY e.name, ba.sort_order;

COMMIT;
