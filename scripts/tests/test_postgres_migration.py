"""Opt-in integration: DSN must name a disposable, empty test database only."""
import os
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import legacy_migration as m

@unittest.skipUnless(os.environ.get('SCM_TEST_POSTGRES_DSN'), 'no disposable PostgreSQL DSN supplied')
class PostgresTests(unittest.TestCase):
    def test_transaction_readback_noop_and_destination_drift(self):
        import psycopg
        dsn = os.environ['SCM_TEST_POSTGRES_DSN']
        with psycopg.connect(dsn, autocommit=True) as conn:
            self.assertIsNone(conn.execute("SELECT to_regnamespace('scm')").fetchone()[0], 'test requires empty disposable database')
            conn.execute((Path(__file__).resolve().parents[2] / 'db/001_scm.sql').read_text())
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'characterdata').mkdir()
            (root / 'characterdata/template.yml').write_text('lastPlayedCharacter: named\nnamed: {enabled: false, name: 001, skinId: missing, skin: inline, title: missing}\nnext: {}')
            (root / 'titles.yml').write_text('Grand Duke: " Duke "')
            (root / 'skin_cache.yml').write_text('skins: [{id: pixel-content-id, mojang_url: "https://textures.minecraft.net/texture/test", texture: exact-texture, signature: exact-signature}]')
            (root / 'uniform_skin_cache.yml').write_text('cache: [{base_url: "https://exact/base", variants: [{hash: variant-hash, url: "https://exact/variant", texture: variant-texture, signature: variant-signature}]}]')
            expected = m.preflight(root)
            # Abort after earlier tables have been inserted: all rows and receipt DDL must roll back.
            with psycopg.connect(dsn) as conn:
                conn.execute("CREATE FUNCTION scm.test_reject_title() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected importer failure'; END $$")
                conn.execute('CREATE TRIGGER test_reject_title BEFORE INSERT ON scm.titles FOR EACH ROW EXECUTE FUNCTION scm.test_reject_title()')
            with self.assertRaises(psycopg.Error):
                m.apply_postgres(dsn, root, expected)
            with psycopg.connect(dsn) as conn:
                self.assertTrue(all(not rows for rows in m.readback(conn).values()))
                self.assertIsNone(conn.execute("SELECT to_regclass('scm.legacy_import')").fetchone()[0])
                conn.execute('DROP TRIGGER test_reject_title ON scm.titles')
                conn.execute('DROP FUNCTION scm.test_reject_title()')
            self.assertEqual(m.apply_postgres(dsn, root, expected), 'insert')
            self.assertEqual(m.apply_postgres(dsn, root, expected), 'noop')
            with psycopg.connect(dsn) as conn:
                self.assertEqual(conn.execute('SELECT name,enabled,skin_id,skin FROM scm.characters ORDER BY ordinal').fetchall(), [('1',False,'missing','inline'),('',True,'','')])
                conn.execute("UPDATE scm.characters SET name='changed'")
            with self.assertRaisesRegex(m.MigrationError, 'destination'):
                m.apply_postgres(dsn, root, expected)
            with psycopg.connect(dsn) as conn:
                self.assertEqual(conn.execute('SELECT DISTINCT name FROM scm.characters').fetchall(), [('changed',)])
