"""
Next2Cash Data Migration Script
Migrates transactions from legacy Excel files to PostgreSQL
ATLAS (next2me) + House (house)
"""

import pandas as pd
import psycopg2
from psycopg2.extras import execute_values
import numpy as np
from datetime import datetime

# ── DB Connection ──────────────────────────────────────────────────
DB_CONFIG = {
    'host': 'next2cash-db.postgres.database.azure.com',
    'port': 5432,
    'dbname': 'next2cash',
    'user': 'next2cash_admin',
    'password': 'Next2Cash@2026!',
    'sslmode': 'require'
}

# ── Entity IDs (from DB) ───────────────────────────────────────────
ENTITY_NEXT2ME = '58202b71-4ddb-45c9-8e3c-39e816bde972'
ENTITY_HOUSE   = 'dea1f32c-7b30-4981-b625-633da9dbe71e'

# ── Admin User ID ──────────────────────────────────────────────────
ADMIN_USER_ID  = 'd4f832d6-08e0-4de7-862d-04c08193de43'  # apostolos

def safe_str(val, max_len=None):
    if val is None or (isinstance(val, float) and np.isnan(val)):
        return None
    s = str(val).strip()
    if max_len:
        s = s[:max_len]
    return s if s else None

def safe_date(val):
    """Return a Python date or None — never NaT or NaN"""
    try:
        if val is None:
            return None
        if isinstance(val, float) and np.isnan(val):
            return None
        ts = pd.Timestamp(val)
        if ts is pd.NaT or pd.isnull(ts):
            return None
        return ts.date()
    except Exception:
        return None

def safe_decimal(val):
    if val is None or (isinstance(val, float) and np.isnan(val)):
        return 0.00
    try:
        return float(val)
    except:
        return 0.00

def map_payment_status(val):
    """Map legacy payment_status to valid DB values"""
    mapping = {
        'paid': 'paid',
        'unpaid': 'unpaid',
        'received': 'received',
        'urgent': 'urgent',
        'partial': 'partial',
        'partially_paid': 'partial',
        'overdue': 'urgent',
    }
    v = safe_str(val, 20)
    if not v:
        return 'unpaid'
    return mapping.get(v.lower(), 'unpaid')

def map_record_status(val):
    v = safe_str(val, 10)
    if not v:
        return 'active'
    return 'void' if v.lower() == 'void' else 'active'

def map_accounting_period(val):
    """Convert accounting_period timestamp to YYYY-MM string"""
    d = safe_date(val)
    if d:
        return d.strftime('%Y-%m')
    return None

def build_blob_folder_path(entity_code, doc_date, txn_id, description):
    """Build Azure Blob folder path: entity/YYYY/MM/"""
    if doc_date is None:
        return f"{entity_code}/unknown/"
    return f"{entity_code}/{doc_date.year}/{doc_date.month:02d}/"

def prepare_transactions(df, entity_id, entity_code):
    """Prepare transactions for bulk insert — handle duplicate IDs"""
    rows = []
    seen_ids = set()
    next_fallback_id = 90000  # For duplicate IDs in legacy data

    for _, row in df.iterrows():
        txn_id = int(row['id']) if pd.notna(row['id']) else None
        if txn_id is None:
            continue
        # Handle duplicate IDs — assign new ID
        if txn_id in seen_ids:
            txn_id = next_fallback_id
            next_fallback_id += 1
        seen_ids.add(txn_id)

        doc_date = safe_date(row.get('doc_date'))
        drive_file_ids = safe_str(row.get('drive_file_ids'))
        description = safe_str(row.get('description'), 1000)
        payment_date = safe_date(row.get('payment_date'))
        due_date = safe_date(row.get('due_date'))
        
        # Build blob folder path for future use
        blob_folder = build_blob_folder_path(
            entity_code, doc_date, txn_id, description
        )

        rows.append((
            txn_id,                                          # id (preserve original)
            entity_id,                                       # entity_id
            safe_str(row.get('type'), 10) or 'expense',     # type
            doc_date,                                        # doc_date
            map_accounting_period(row.get('accounting_period')),  # accounting_period
            safe_str(row.get('counterparty'), 200),          # counterparty
            safe_str(row.get('account'), 200),               # account
            safe_str(row.get('category'), 200),              # category
            safe_str(row.get('subcategory'), 200),           # subcategory
            description,                                     # description
            safe_decimal(row.get('amount')),                 # amount
            safe_decimal(row.get('amount_paid')),            # amount_paid
            safe_decimal(row.get('amount_remaining')),       # amount_remaining
            safe_str(row.get('payment_method'), 100),        # payment_method
            map_payment_status(row.get('payment_status')),   # payment_status
            payment_date,                                    # payment_date
            due_date,                                        # due_date
            None,                                            # doc_status
            drive_file_ids,                                  # blob_file_ids
            blob_folder,                                     # blob_folder_path
            map_record_status(row.get('record_status')),     # record_status
            bool(row.get('approved')) if pd.notna(row.get('approved', None)) else False,  # approved
            ADMIN_USER_ID,                                   # created_by
            ADMIN_USER_ID,                                   # updated_by
        ))
    return rows

def migrate(conn, df, entity_id, entity_code, entity_name):
    rows = prepare_transactions(df, entity_id, entity_code)
    print(f"\n{entity_name}: {len(rows)} transactions to migrate...")

    with conn.cursor() as cur:
        # Check existing IDs to avoid duplicates
        cur.execute("SELECT id FROM transactions WHERE entity_id = %s", (entity_id,))
        existing = {r[0] for r in cur.fetchall()}
        
        new_rows = [r for r in rows if r[0] not in existing]
        skip = len(rows) - len(new_rows)
        
        if skip > 0:
            print(f"  Skipping {skip} already existing transactions")
        
        if not new_rows:
            print(f"  Nothing to insert.")
            return 0

        # Force sequence to not conflict with our IDs
        max_id = max(r[0] for r in new_rows)

        execute_values(cur, """
            INSERT INTO transactions (
                id, entity_id, type, doc_date, accounting_period,
                counterparty, account, category, subcategory, description,
                amount, amount_paid, amount_remaining, payment_method,
                payment_status, payment_date, due_date, doc_status,
                blob_file_ids, blob_folder_path, record_status, approved,
                created_by, updated_by
            ) VALUES %s
            ON CONFLICT (id) DO NOTHING
        """, new_rows)

        # Update sequence to max ID + 1
        cur.execute(f"SELECT setval('transactions_id_seq', {max_id + 1}, false)")
        
        conn.commit()
        print(f"  ✅ Inserted {len(new_rows)} transactions (max_id={max_id})")
        return len(new_rows)

def main():
    print("=" * 60)
    print("Next2Cash Data Migration")
    print("=" * 60)

    # Load Excel files
    print("\nLoading Excel files...")
    atlas_df = pd.read_excel('ATLAS_Financial_Database.xlsx', sheet_name='Transactions')
    house_df = pd.read_excel('CashControl___House.xlsx', sheet_name='Transactions')
    print(f"  ATLAS (next2me): {len(atlas_df)} rows")
    print(f"  House: {len(house_df)} rows")

    # Connect to DB
    print("\nConnecting to PostgreSQL...")
    conn = psycopg2.connect(**DB_CONFIG)
    print("  ✅ Connected")

    try:
        # Migrate ATLAS → next2me
        total1 = migrate(conn, atlas_df, ENTITY_NEXT2ME, 'next2me', 'Next2Me (ATLAS)')
        
        # Migrate House → house
        # House IDs might conflict with ATLAS IDs → offset by 100000
        print("\nChecking House IDs for conflicts with Next2Me IDs...")
        atlas_ids = set(atlas_df['id'].dropna().astype(int).tolist())
        house_ids = set(house_df['id'].dropna().astype(int).tolist())
        conflicts = atlas_ids & house_ids
        if conflicts:
            print(f"  ⚠️  {len(conflicts)} ID conflicts found — offsetting House IDs by 100000")
            house_df = house_df.copy()
            house_df['id'] = house_df['id'].apply(lambda x: int(x) + 100000 if pd.notna(x) else x)
        
        total2 = migrate(conn, house_df, ENTITY_HOUSE, 'house', 'House')

        print(f"\n{'='*60}")
        print(f"Migration Complete!")
        print(f"  Next2Me: {total1} transactions")
        print(f"  House:   {total2} transactions")
        print(f"  TOTAL:   {total1 + total2} transactions")
        print(f"{'='*60}")

    except Exception as e:
        conn.rollback()
        print(f"\n❌ ERROR: {e}")
        raise
    finally:
        conn.close()

if __name__ == '__main__':
    main()
