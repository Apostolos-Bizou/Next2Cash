"""
Next2Cash Config Migration Script
Migrates config (categories, accounts, payment methods etc.) from legacy Excel to PostgreSQL
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

# Skip these config types — not relevant for new system
SKIP_TYPES = {'setting', 'card'}  # settings are app-specific, cards are legacy

def safe_str(val, max_len=None):
    if val is None or (isinstance(val, float) and np.isnan(val)):
        return None
    s = str(val).strip()
    if max_len:
        s = s[:max_len]
    return s if s else None

def safe_bool(val):
    if val is None or (isinstance(val, float) and np.isnan(val)):
        return True
    if isinstance(val, bool):
        return val
    if isinstance(val, str):
        return val.lower() not in ('false', '0', 'no')
    return bool(val)

def migrate_config(conn, df, entity_id, entity_name):
    rows = []
    for _, row in df.iterrows():
        config_type = safe_str(row.get('config_type'), 50)
        config_key  = safe_str(row.get('config_key'), 100)

        if not config_type or not config_key:
            continue

        # Skip irrelevant types
        if config_type in SKIP_TYPES:
            continue

        rows.append((
            entity_id,                                       # entity_id
            config_type,                                     # config_type
            config_key,                                      # config_key
            safe_str(row.get('config_value'), 500),          # config_value
            safe_str(row.get('parent_key'), 100),            # parent_key
            int(row.get('sort_order', 0)) if pd.notna(row.get('sort_order')) else 0,  # sort_order
            safe_bool(row.get('active')),                    # is_active
        ))

    print(f"\n{entity_name}: {len(rows)} config entries to migrate...")

    with conn.cursor() as cur:
        # Check existing
        cur.execute(
            "SELECT config_key, config_type FROM config WHERE entity_id = %s",
            (entity_id,)
        )
        existing = {(r[0], r[1]) for r in cur.fetchall()}
        new_rows = [r for r in rows if (r[2], r[1]) not in existing]
        skip = len(rows) - len(new_rows)

        if skip > 0:
            print(f"  Skipping {skip} already existing config entries")

        if not new_rows:
            print(f"  Nothing to insert.")
            return 0

        execute_values(cur, """
            INSERT INTO config (
                entity_id, config_type, config_key,
                config_value, parent_key, sort_order, is_active
            ) VALUES %s
            ON CONFLICT DO NOTHING
        """, new_rows)

        conn.commit()
        print(f"  ✅ Inserted {len(new_rows)} config entries")
        return len(new_rows)

def main():
    print("=" * 60)
    print("Next2Cash Config Migration")
    print("=" * 60)

    print("\nLoading Excel files...")
    atlas_df = pd.read_excel('ATLAS_Financial_Database.xlsx', sheet_name='Config')
    house_df = pd.read_excel('CashControl___House.xlsx', sheet_name='Config')
    print(f"  ATLAS (next2me): {len(atlas_df)} config rows")
    print(f"  House:           {len(house_df)} config rows")

    print("\nConnecting to PostgreSQL...")
    conn = psycopg2.connect(**DB_CONFIG)
    print("  ✅ Connected")

    try:
        total1 = migrate_config(conn, atlas_df, ENTITY_NEXT2ME, 'Next2Me (ATLAS)')
        total2 = migrate_config(conn, house_df, ENTITY_HOUSE, 'House')

        print(f"\n{'='*60}")
        print(f"Config Migration Complete!")
        print(f"  Next2Me: {total1} entries")
        print(f"  House:   {total2} entries")
        print(f"  TOTAL:   {total1 + total2} entries")
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
