"""
Next2Cash Payments Migration Script
Migrates payments from legacy Excel files to PostgreSQL
"""

import pandas as pd
import psycopg2
from psycopg2.extras import execute_values
import numpy as np

DB_CONFIG = {
    'host': 'next2cash-db.postgres.database.azure.com',
    'port': 5432,
    'dbname': 'next2cash',
    'user': 'next2cash_admin',
    'password': 'Next2Cash@2026!',
    'sslmode': 'require'
}

ENTITY_NEXT2ME = '58202b71-4ddb-45c9-8e3c-39e816bde972'
ENTITY_HOUSE   = 'dea1f32c-7b30-4981-b625-633da9dbe71e'
ADMIN_USER_ID  = 'd4f832d6-08e0-4de7-862d-04c08193de43'

def safe_str(val, max_len=None):
    if val is None or (isinstance(val, float) and np.isnan(val)):
        return None
    s = str(val).strip()
    if max_len:
        s = s[:max_len]
    return s if s else None

def safe_date(val):
    try:
        if val is None:
            return None
        if isinstance(val, float) and np.isnan(val):
            return None
        ts = pd.Timestamp(val)
        if pd.isnull(ts):
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

def map_payment_period(val):
    """Convert payment_period like '3-23-6-' to YYYY-MM"""
    s = safe_str(val)
    if not s:
        return None
    # Try direct YYYY-MM format
    try:
        ts = pd.Timestamp(s)
        if not pd.isnull(ts):
            return ts.strftime('%Y-%m')
    except:
        pass
    return s[:20] if s else None

def migrate_payments(conn, df, entity_id, entity_name, id_col, txn_offset=0):
    """Migrate payments for one entity"""
    rows = []
    seen_ids = set()
    next_fallback_id = 90000

    for _, row in df.iterrows():
        pay_id = int(row[id_col]) if pd.notna(row.get(id_col)) else None
        if pay_id is None:
            continue
        if pay_id in seen_ids:
            pay_id = next_fallback_id
            next_fallback_id += 1
        seen_ids.add(pay_id)

        # Handle transaction_id offset for house
        txn_id = row.get('transaction_id')
        if pd.notna(txn_id):
            txn_id = int(txn_id) + txn_offset
        else:
            txn_id = None

        payment_date = safe_date(row.get('payment_date'))
        if payment_date is None:
            print(f"  ⚠️  Skipping payment {pay_id} — no payment_date")
            continue

        rows.append((
            pay_id,                                          # id
            entity_id,                                       # entity_id
            txn_id,                                          # transaction_id
            payment_date,                                    # payment_date
            map_payment_period(row.get('payment_period')),   # payment_period
            safe_str(row.get('payment_type'), 20) or 'outgoing',  # payment_type
            safe_decimal(row.get('amount')),                 # amount
            safe_str(row.get('payment_method'), 100),        # payment_method
            safe_str(row.get('bank_reference'), 200),        # bank_reference
            None,                                            # bank_account_id
            safe_str(row.get('counterparty'), 200),          # counterparty
            safe_str(row.get('description')),                # description
            safe_str(row.get('status'), 20) or 'completed', # status
            safe_str(row.get('drive_file_ids'), 500),        # blob_file_id
            safe_str(row.get('notes')),                      # notes
            ADMIN_USER_ID,                                   # created_by
        ))

    print(f"\n{entity_name}: {len(rows)} payments to migrate...")

    with conn.cursor() as cur:
        cur.execute("SELECT id FROM payments WHERE entity_id = %s", (entity_id,))
        existing = {r[0] for r in cur.fetchall()}
        new_rows = [r for r in rows if r[0] not in existing]
        skip = len(rows) - len(new_rows)

        if skip > 0:
            print(f"  Skipping {skip} already existing payments")

        if not new_rows:
            print(f"  Nothing to insert.")
            return 0

        max_id = max(r[0] for r in new_rows)

        execute_values(cur, """
            INSERT INTO payments (
                id, entity_id, transaction_id, payment_date, payment_period,
                payment_type, amount, payment_method, bank_reference,
                bank_account_id, counterparty, description, status,
                blob_file_id, notes, created_by
            ) VALUES %s
            ON CONFLICT (id) DO NOTHING
        """, new_rows)

        cur.execute(f"SELECT setval('payments_id_seq', {max_id + 1}, false)")
        conn.commit()
        print(f"  ✅ Inserted {len(new_rows)} payments (max_id={max_id})")
        return len(new_rows)

def main():
    print("=" * 60)
    print("Next2Cash Payments Migration")
    print("=" * 60)

    print("\nLoading Excel files...")
    atlas_df = pd.read_excel('ATLAS_Financial_Database.xlsx', sheet_name='Payments')
    house_df = pd.read_excel('CashControl___House.xlsx', sheet_name='Payments')
    print(f"  ATLAS (next2me): {len(atlas_df)} payments")
    print(f"  House:           {len(house_df)} payments")

    print("\nConnecting to PostgreSQL...")
    conn = psycopg2.connect(**DB_CONFIG)
    print("  ✅ Connected")

    try:
        # ATLAS payments — id column is 'payment_id', no offset
        total1 = migrate_payments(conn, atlas_df, ENTITY_NEXT2ME, 'Next2Me (ATLAS)', 'payment_id', txn_offset=0)

        # House payments — id column is 'id', txn_id offset +100000
        total2 = migrate_payments(conn, house_df, ENTITY_HOUSE, 'House', 'id', txn_offset=100000)

        print(f"\n{'='*60}")
        print(f"Payments Migration Complete!")
        print(f"  Next2Me: {total1} payments")
        print(f"  House:   {total2} payments")
        print(f"  TOTAL:   {total1 + total2} payments")
        print(f"{'='*60}")

    except Exception as e:
        conn.rollback()
        print(f"\n❌ ERROR: {e}")
        import traceback
        traceback.print_exc()
        raise
    finally:
        conn.close()

if __name__ == '__main__':
    main()
