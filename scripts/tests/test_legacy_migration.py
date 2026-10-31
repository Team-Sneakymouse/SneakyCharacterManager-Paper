import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import legacy_migration as m

class YamlTests(unittest.TestCase):
    def test_snake_yaml_22_scalar_contract(self):
        # Observed with new Yaml().load using the project's SnakeYAML 2.2 JAR.
        self.assertEqual(m.load_yaml('id: 001\nflag: yes\nquoted: "001"'), {'id':1,'flag':True,'quoted':'001'})
    def test_unsupported_ambiguous_types_refused(self):
        for text in ['date: 2026-01-01', 'float: 1.25', 'float: 1e3', 'float: 1.0e3']:
            with self.subTest(text=text), self.assertRaises(m.MigrationError): m.load_yaml(text)
        for value in [[], {}, [None]]:
            with self.assertRaises(m.MigrationError): m.scalar(value)
    def test_null_and_quoted_null(self):
        self.assertEqual(m.load_yaml('a: null\nb: ~\nc: "null"'), {'a': None, 'b': None, 'c': 'null'})
    def test_custom_tag_refused(self):
        with self.assertRaises(m.MigrationError): m.load_yaml('a: !custom value')
    def test_duplicate_including_merge(self):
        for text in ['a: 1\na: 2', 'base: &b {a: 1}\nx: {<<: *b, a: 2}', 'a: [']:
            with self.subTest(text=text), self.assertRaises(m.MigrationError): m.load_yaml(text)

class ManifestTests(unittest.TestCase):
    def setUp(self):
        import tempfile
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / 'characterdata').mkdir()
    def put(self, name, text):
        (self.root / name).write_text(text)
    def test_preserve_order_disabled_template_and_skin(self):
        self.put('characterdata/template.yml', 'lastPlayedCharacter: second\nfirst:\n  enabled: false\n  skinId: abc\n  skin: inline\n  name: 001\n  tags: [a, b]\n  gender: FEMININE\nsecond: {}\n')
        self.put('keys.ser', 'not serialized: never read')
        value = m.preflight(self.root)
        chars = value['tables']['characters']
        self.assertEqual([c['character_id'] for c in chars], ['first','second'])
        self.assertFalse(chars[0]['enabled'])
        self.assertEqual((chars[0]['skin_id'], chars[0]['skin'], chars[0]['name'], chars[0]['tags'], chars[0]['gender']), ('abc','inline','1','a,b','feminine'))
        self.assertEqual(value['tables']['owners'][0]['owner_id'], 'template')
        self.assertNotIn('keys.ser', value['files'])
        self.assertEqual(value, m.preflight(self.root))
    def test_player_scalar_defaults_and_tag_java_null(self):
        self.put('characterdata/template.yml', 'x: {enabled: 1, slim: yes, name: 001, tags: [null, TRUE, 001]}')
        row = m.preflight(self.root)['tables']['characters'][0]
        self.assertEqual((row['enabled'], row['slim'], row['name'], row['tags']), (True, True, '1', 'null,true,1'))
    def test_opaque_skin_uuids_preserved(self):
        self.put('characterdata/template.yml', 'x: {skinUUID: abc123, skinId: "001"}')
        self.put('uniform_skin_cache.yml', 'cache: [{base_url: exact, variants: [{hash: h, uuid: "001abc"}]}]')
        value = m.preflight(self.root)
        self.assertEqual(value['tables']['characters'][0]['skin_uuid'], 'abc123')
        self.assertEqual(value['tables']['characters'][0]['skin_id'], '001')
        self.assertEqual(value['tables']['uniform_variants'][0]['skin_uuid'], '001abc')
    def test_title_skips_before_collision_with_manifest_diagnostics(self):
        self.put('titles.yml', 'duke: null\nDuke: "  "\ndu-ke: Kept\n"!!!": Ignored\n')
        value = m.preflight(self.root)
        self.assertEqual(value['tables']['titles'], [{'code':'du_ke','display':'Kept'}])
        self.assertEqual([d['reason'] for d in value['diagnostics']], ['null display','empty display','empty normalized code'])
        self.assertTrue(all(d['source'] == 'titles.yml' for d in value['diagnostics']))
        self.assertIn('titles.yml', value['files'])
    def test_java_title_trim(self):
        self.assertEqual(m.normalize_title('\u00a0duke\u00a0'), 'duke')
        self.put('titles.yml', 'duke: "\u00a0Duke\u00a0"')
        self.assertEqual(m.preflight(self.root)['tables']['titles'][0]['display'], '\u00a0Duke\u00a0')
    def test_bad_owner_rejected_not_normalized(self):
        self.put('characterdata/ABC.yml', 'lastPlayedCharacter: x\nx: {}')
        with self.assertRaisesRegex(m.MigrationError, 'owner'): m.preflight(self.root)
    def test_collisions(self):
        for name, text in [
            ('titles.yml', 'Grand Duke: Duke\ngrand-duke: Other'),
            ('skin_cache.yml', 'skins:\n- {id: a, mojang_url: "https://textures.minecraft.net/texture/x"}\n- {id: b, mojang_url: "http://textures.minecraft.net/texture/x"}'),
            ('uniform_skin_cache.yml', 'cache:\n- base_url: exact\n  variants: [{hash: x}, {hash: x}]')]:
            with self.subTest(name=name):
                self.put(name, text)
                with self.assertRaisesRegex(m.MigrationError, 'collision'): m.preflight(self.root)
                (self.root / name).unlink()
    def test_cache_success_and_source_untouched(self):
        self.put('skin_cache.yml', 'skins: [{id: 001, mojang_url: "https://textures.minecraft.net/texture/a", texture: raw, signature: sig}]')
        self.put('uniform_skin_cache.yml', 'cache: [{base_url: "https://exact", variants: [{hash: 001, url: "https://exact/variant"}]}]')
        self.put('titles.yml', 'Grand - Duke!!: " Duke "')
        before = {p: p.read_bytes() for p in self.root.iterdir() if p.is_file()}
        value = m.preflight(self.root)
        self.assertEqual(value['tables']['skins'][0]['skin_id'], '1')
        self.assertEqual(value['tables']['skins'][0]['mojang_url'], 'http://textures.minecraft.net/texture/a')
        self.assertEqual(value['tables']['uniform_variants'][0]['base_url'], 'https://exact')
        self.assertEqual(value['tables']['titles'], [{'code':'grand_duke','display':'Duke'}])
        self.assertEqual(before, {p:p.read_bytes() for p in before})
    def test_snapshot_change_and_nested_paper_refused(self):
        self.put('titles.yml', 'duke: Duke')
        value = m.preflight(self.root)
        self.put('titles.yml', 'duke: Other')
        with self.assertRaisesRegex(m.MigrationError, 'changed'): m.verify_snapshot(self.root, value)
        (self.root / 'characterdata' / 'nested').mkdir()
        with self.assertRaisesRegex(m.MigrationError, 'nested'): m.preflight(self.root)

class CliTests(unittest.TestCase):
    setUp = ManifestTests.setUp
    put = ManifestTests.put
    def test_dry_run_no_db_and_apply_requires_freeze(self):
        manifest = self.root.parent / (self.root.name + '-manifest.json')
        self.addCleanup(lambda: manifest.unlink(missing_ok=True))
        self.assertEqual(m.main([str(self.root), '--manifest', str(manifest)]), 0)
        self.assertTrue(manifest.exists())
        self.assertEqual(m.main([str(self.root), '--manifest', str(manifest), '--apply']), 2)
    def test_uncertain_commit_error_does_not_claim_rollback(self):
        from unittest.mock import patch
        import contextlib
        import io
        manifest = self.root.parent / (self.root.name + '-manifest.json')
        self.addCleanup(lambda: manifest.unlink(missing_ok=True))
        self.assertEqual(m.main([str(self.root), '--manifest', str(manifest)]), 0)
        stderr = io.StringIO()
        with patch.dict(m.os.environ, {'SCM_MIGRATION_DSN':'test-only'}), patch.object(m, 'apply_postgres', side_effect=RuntimeError('secret commit connection lost')), contextlib.redirect_stderr(stderr):
            self.assertEqual(m.main([str(self.root), '--manifest', str(manifest), '--apply', '--writers-stopped']), 2)
        self.assertIn('outcome unknown', stderr.getvalue())
        self.assertIn('keep writers stopped', stderr.getvalue())
        self.assertNotIn('rolled back', stderr.getvalue())
        self.assertNotIn('secret', stderr.getvalue())
    def test_destination_decision(self):
        self.assertEqual(m.destination_action('abc', 'abc', True), 'noop')
        self.assertEqual(m.destination_action(None, 'abc', False), 'insert')
        for receipt, same in [('abc',False),('other',True),(None,True)]:
            with self.assertRaises(m.MigrationError): m.destination_action(receipt, 'abc', same)

if __name__ == '__main__': unittest.main()
