"""
Next2Cash Bank Accounts Migration Script
Migrates bank accounts from legacy Excel to PostgreSQL
NOTE: Only migrates ACTIVE accounts (active=True)
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

def map_account_type(val):
    """Map legacy account types to valid DB values"""
    mapping = {
        'checking': 'checking',
        'savings':  'savings',
        'cash':     'cash',
        'credit':   'credit',
        'revolut':  'checking',  # revolut → checking
    }
    v = safe_str(val, 20)
    if not v:
        return 'checking'
    return mapping.get(v.lower(), 'checking')

def migrate_bank_accounts(conn, df, entity_id, entity_name):
    """Migrate ACTIVE bank accounts only"""
    rows = []

    # Only active accounts
    active_df = df[df['active'] == True].copy()
    print(f"\n{entity_name}: {len(active_df)} active accounts (of {len(df)} total)")

    for _, row in active_df.iterrows():
        bank_name = safe_str(row.get('bank_name'), 100)
        if not bank_name:
            continue

        rows.append((
            entity_id,                                           # entity_id
            bank_name,                                           # bank_name
            safe_str(row.get('account_label'), 100),             # account_label
            safe_str(row.get('account_number'), 50),             # account_number
            safe_str(row.get('iban'), 34),                       # iban
            map_account_type(row.get('account_type')),           # account_type
            safe_str(row.get('currency'), 10) or 'EUR',          # currency
            safe_decimal(row.get('current_balance')),            # current_balance
            safe_date(row.get('balance_date')),                  # balance_date
            safe_str(row.get('linked_config_account'), 200),     # linked_config_key
            True,                                                # is_active
            int(row.get('sort_order', 0)) if pd.notna(row.get('sort_order')) else 0,  # sort_order
            safe_str(row.get('notes')),                          # notes
            ADMIN_USER_ID,                                       # updated_by
        ))

    if not rows:
        print(f"  Nothing to insert.")
        return 0

    with conn.cursor() as cur:
        # Check existing by bank_name + account_label + entity
        cur.execute(
            "SELECT bank_name, account_label FROM bank_accounts WHERE entity_id = %s",
            (entity_id,)
        )
        existing = {(r[0], r[1]) for r in cur.fetchall()}
        new_rows = [r for r in rows if (r[1], r[2]) not in existing]
        skip = len(rows) - len(new_rows)

        if skip > 0:
            print(f"  Skipping {skip} already existing accounts")

        if not new_rows:
            print(f"  Nothing to insert.")
            return 0

        execute_values(cur, """
            INSERT INTO bank_accounts (
                entity_id, bank_name, account_label, account_number,
                iban, account_type, currency, current_balance, balance_date,
                linked_config_key, is_active, sort_order, notes, updated_by
            ) VALUES %s
        """, new_rows)

        conn.commit()
        print(f"  ✅ Inserted {len(new_rows)} bank accounts")
        return len(new_rows)

def main():
    print("=" * 60)
    print("Next2Cash Bank Accounts Migration")
    print("=" * 60)

    print("\nLoading Excel files...")
    atlas_df = pd.read_excel('ATLAS_Financial_Database.xlsx', sheet_name='BankAccounts')
    house_df = pd.read_excel('CashControl___House.xlsx', sheet_name='BankAccounts')
    print(f"  ATLAS (next2me): {len(atlas_df)} accounts")
    print(f"  House:           {len(house_df)} accounts")

    print("\nConnecting to PostgreSQL...")
    conn = psycopg2.connect(**DB_CONFIG)
    print("  ✅ Connected")

    try:
        total1 = migrate_bank_accounts(conn, atlas_df, ENTITY_NEXT2ME, 'Next2Me (ATLAS)')
        total2 = migrate_bank_accounts(conn, house_df, ENTITY_HOUSE, 'House')

        print(f"\n{'='*60}")
        print(f"Bank Accounts Migration Complete!")
        print(f"  Next2Me: {total1} accounts")
        print(f"  House:   {total2} accounts")
        print(f"  TOTAL:   {total1 + total2} accounts")
        print(f"{'='*60}")

        # Show final balances
        print("\nFinal bank balances in DB:")
        conn2 = psycopg2.connect(**DB_CONFIG)
        with conn2.cursor() as cur:
            cur.execute("""
                SELECT e.code, b.account_label, b.currency, b.current_balance
                FROM bank_accounts b
                JOIN entities e ON e.id = b.entity_id
                WHERE b.is_active = true
                ORDER BY e.code, b.sort_order
            """)
            for row in cur.fetchall():
                print(f"  {row[0]:10} | {row[1]:20} | {row[2]} | {row[3]:>12.2f}")
        conn2.close()

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
