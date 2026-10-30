#!/usr/bin/env python3
"""Offline, explicit SCM migration. Never imported by the plugin."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import uuid
import yaml

class MigrationError(ValueError):
    pass

class StrictLoader(yaml.SafeLoader):
    def construct_object(self, node, deep=False):
        allowed = {'tag:yaml.org,2002:' + name for name in ('str', 'map', 'seq', 'null', 'bool', 'int')}
        if node.tag not in allowed:
            raise MigrationError(f'unsupported YAML tag: {node.tag}')
        return super().construct_object(node, deep=deep)

    def construct_scalar(self, node):
        # SnakeYAML also resolves exponent-only floats that PyYAML leaves text.
        if node.tag.endswith(':str') and node.style is None and re.fullmatch(r'[-+]?(?:[0-9][0-9_]*(?:\.[0-9_]*)?|\.[0-9_]+)[eE][-+]?[0-9]+', node.value):
            raise MigrationError('unsupported YAML floating-point scalar')
        return super().construct_scalar(node)

    def construct_mapping(self, node, deep=False):
        # Resolve merge keys before checking: inherited overrides are ambiguous too.
        result = {}
        for key_node, value_node in node.value:
            key = '<<' if key_node.tag == 'tag:yaml.org,2002:merge' else self.construct_object(key_node, deep=True)
            if not isinstance(key, str):
                raise MigrationError('non-scalar mapping key')
            if key == '<<':
                inherited = self.construct_object(value_node, deep=True)
                maps = inherited if isinstance(inherited, list) else [inherited]
                for mapping in maps:
                    if not isinstance(mapping, dict):
                        raise MigrationError('invalid YAML merge')
                    for k, v in mapping.items():
                        if k in result: raise MigrationError(f'duplicate inherited key {k!r}')
                        result[k] = v
            else:
                if key in result: raise MigrationError(f'duplicate key {key!r}')
                result[key] = self.construct_object(value_node, deep=True)
        return result

def load_yaml(text):
    try:
        value = yaml.load(text, Loader=StrictLoader)
        if not isinstance(value, dict): raise MigrationError('expected YAML mapping')
        return value
    except (yaml.YAMLError, RecursionError) as exc:
        raise MigrationError(f'malformed YAML: {exc}') from exc

def scalar(value):
    if value is None: return ''
    if isinstance(value, bool): value = 'true' if value else 'false'
    elif isinstance(value, int): value = str(value)
    if not isinstance(value, str): raise MigrationError('unsupported Java string conversion (collection or scalar type)')
    if '\x00' in value: raise MigrationError('NUL not supported by PostgreSQL text')
    return value

def boolean(value, default):
    if isinstance(value, bool): return value
    if isinstance(value, str): return value.lower() == 'true'
    return default

def mapping(value):
    if not isinstance(value, dict): raise MigrationError('expected mapping')
    return value

def sequence(value):
    if not isinstance(value, list): raise MigrationError('expected list')
    return value

def canonical_uuid(value, context):
    try:
        if str(uuid.UUID(value)) != value: raise ValueError()
    except ValueError:
        raise MigrationError(f'invalid canonical UUID {context}: {value!r}') from None

def java_trim(value):
    return re.sub(r'^[\x00-\x20]+|[\x00-\x20]+$', '', scalar(value))

def normalize_title(value):
    value = java_trim(value).lower().replace(' ', '_').replace('-', '_')
    return re.sub('_+', '_', re.sub('[^a-z0-9_]', '', value)).strip('_')

def normalize_url(value):
    value = scalar(value)
    prefix = 'https://textures.minecraft.net/texture/'
    return 'http://' + value[len('https://'):] if value.startswith(prefix) else value

def source_paths(root):
    if not root.is_dir(): raise MigrationError('source must be proxy snapshot directory')
    folder = root / 'characterdata'
    if not folder.is_dir() or folder.is_symlink(): raise MigrationError('missing real characterdata directory')
    paths = []
    for path in sorted(folder.iterdir()):
        if path.is_symlink(): raise MigrationError(f'symlink refused: {path.name}')
        if path.is_dir(): raise MigrationError('nested Paper characterdata is excluded; use proxy snapshot')
        if path.suffix != '.yml': raise MigrationError(f'unexpected characterdata file: {path.name}')
        paths.append(path)
    for name in ('skin_cache.yml', 'uniform_skin_cache.yml', 'titles.yml'):
        path = root / name
        if path.is_symlink(): raise MigrationError(f'symlink refused: {name}')
        if path.exists(): paths.append(path)
    return paths

def preflight(root):
    root = Path(root)
    tables = {name: [] for name in ('owners','characters','skins','skin_sources','uniform_variants','titles')}
    files = {}
    diagnostics = []
    seen = {}
    def add(table, row, key):
        marker = (table, key)
        if marker in seen: raise MigrationError(f'collision in {table}: {key!r}')
        seen[marker] = True
        tables[table].append(row)
    for path in source_paths(root):
        raw = path.read_bytes()
        relative = path.relative_to(root).as_posix()
        files[relative] = {'sha256': hashlib.sha256(raw).hexdigest(), 'bytes': len(raw)}
        try: data = load_yaml(raw.decode('utf-8'))
        except (MigrationError, UnicodeError) as exc: raise MigrationError(f'{relative}: {exc}') from exc
        if path.parent.name == 'characterdata':
            owner = path.stem
            if owner != 'template': canonical_uuid(owner, 'owner')
            add('owners', {'owner_id': owner, 'last_played_character': scalar(data.get('lastPlayedCharacter')), 'revision': 0}, owner)
            for key, section in data.items():
                if key.lower() == 'lastplayedcharacter': continue
                scalar(key)
                if not key: raise MigrationError('empty character ID')
                mapping(section)
                row = {'owner_id': owner, 'character_id': key, 'ordinal': len(tables['characters']) + 1,
                       'enabled': boolean(section.get('enabled'), True), 'slim': boolean(section.get('slim'), False)}
                for source, target in [('name','name'),('skinId','skin_id'),('skin','skin'),('skinUUID','skin_uuid'),('texture','texture'),('signature','signature'),('title','title')]:
                    row[target] = scalar(section.get(source))
                tags = section.get('tags')
                row['tags'] = ','.join('null' if v is None else scalar(v) for v in tags) if isinstance(tags, list) else scalar(tags)
                gender = scalar(section.get('gender')).lower()
                row['gender'] = gender if gender in ('masculine','feminine','nonbinary') else ''
                add('characters', row, (owner,key))
        elif path.name == 'skin_cache.yml':
            for entry in sequence(data.get('skins', [])):
                mapping(entry)
                row = {'skin_id': scalar(entry.get('id')), 'mojang_url': normalize_url(entry.get('mojang_url')),
                       'texture': scalar(entry.get('texture')), 'signature': scalar(entry.get('signature'))}
                if not row['skin_id']: raise MigrationError('empty skin ID')
                add('skins', row, row['skin_id'])
                if row['mojang_url']: add('skin_sources', {'source_url': row['mojang_url'], 'skin_id': row['skin_id']}, row['mojang_url'])
        elif path.name == 'uniform_skin_cache.yml':
            bases = set()
            for entry in sequence(data.get('cache', [])):
                mapping(entry)
                base = scalar(entry.get('base_url'))
                if not base: raise MigrationError('empty uniform base URL')
                if base in bases: raise MigrationError(f'collision uniform base: {base!r}')
                bases.add(base)
                for variant in sequence(entry.get('variants', [])):
                    mapping(variant)
                    row = {'base_url': base}
                    for source, target in [('hash','uniform_hash'),('uuid','skin_uuid'),('url','texture_url'),('texture','texture'),('signature','signature')]: row[target] = scalar(variant.get(source))
                    add('uniform_variants', row, (base,row['uniform_hash']))
        else:
            for key, display in data.items():
                code = normalize_title(key)
                reason = 'empty normalized code' if not code else 'null display' if display is None else None
                if reason is None:
                    display = java_trim(display)
                    if not display: reason = 'empty display'
                if reason:
                    diagnostics.append({'source': relative, 'key': key, 'reason': reason})
                    continue
                add('titles', {'code': code, 'display': display}, code)
    value = {'format': 1, 'schema_version': 1, 'source': str(root.resolve()), 'files': files, 'tables': tables, 'diagnostics': diagnostics}
    value['sha256'] = hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
    return value

def verify_snapshot(root, expected):
    if preflight(root) != expected: raise MigrationError('source snapshot or manifest changed')

def destination_action(receipt, digest, same):
    if receipt == digest and same: return 'noop'
    if receipt is None and not same: return 'insert'
    raise MigrationError('destination changed or belongs to another import; refusing writes')

COLUMNS = {
    'owners': ('owner_id','last_played_character','revision'),
    'characters': ('owner_id','character_id','ordinal','enabled','name','skin_id','skin','skin_uuid','texture','signature','slim','tags','gender','title'),
    'skins': ('skin_id','mojang_url','texture','signature'),
    'skin_sources': ('source_url','skin_id'),
    'uniform_variants': ('base_url','uniform_hash','skin_uuid','texture_url','texture','signature'),
    'titles': ('code','display'),
}

def readback(conn):
    # Identifiers are a fixed internal allowlist, never taken from manifest input.
    return {table: [dict(zip(columns, row)) for row in conn.execute(
        f"SELECT {','.join(columns)} FROM scm.{table}").fetchall()]
        for table, columns in COLUMNS.items()}

def equal_tables(actual, expected):
    order = lambda rows: sorted(json.dumps(row, sort_keys=True) for row in rows)
    return all(order(actual[table]) == order(expected[table]) for table in COLUMNS)

def apply_postgres(dsn, root, manifest):
    import psycopg
    from psycopg.types.json import Jsonb
    verify_snapshot(root, manifest)
    with psycopg.connect(dsn) as conn:
        conn.execute('SET TRANSACTION ISOLATION LEVEL SERIALIZABLE')
        conn.execute("SET LOCAL lock_timeout = '10s'")
        conn.execute("SET LOCAL statement_timeout = '120s'")
        version = conn.execute('SELECT version FROM scm.schema_version WHERE singleton = true').fetchone()
        if version != (1,): raise MigrationError('unsupported SCM schema version')
        conn.execute('LOCK TABLE ' + ','.join('scm.' + table for table in COLUMNS) + ' IN ACCESS EXCLUSIVE MODE')
        # Durable provenance is created only by explicit offline apply, transactionally.
        conn.execute('CREATE TABLE IF NOT EXISTS scm.legacy_import (singleton boolean PRIMARY KEY CHECK (singleton), manifest_sha256 text NOT NULL, manifest jsonb NOT NULL, imported_at timestamptz NOT NULL DEFAULT now())')
        conn.execute('LOCK TABLE scm.legacy_import IN ACCESS EXCLUSIVE MODE')
        receipt = conn.execute('SELECT manifest_sha256 FROM scm.legacy_import WHERE singleton = true').fetchone()
        actual = readback(conn)
        same = equal_tables(actual, manifest['tables'])
        if receipt is None and any(actual.values()):
            raise MigrationError('destination is not empty and has no matching import provenance')
        action = destination_action(receipt[0] if receipt else None, manifest['sha256'], same if receipt else False)
        if action == 'noop':
            verify_snapshot(root, manifest)
            return action
        for table, columns in COLUMNS.items():
            query = f"INSERT INTO scm.{table} ({','.join(columns)}) VALUES ({','.join(['%s'] * len(columns))})"
            for row in manifest['tables'][table]: conn.execute(query, tuple(row[column] for column in columns))
        if not equal_tables(readback(conn), manifest['tables']):
            raise MigrationError('field-level destination readback mismatch; rolling back')
        verify_snapshot(root, manifest)
        conn.execute('INSERT INTO scm.legacy_import (singleton,manifest_sha256,manifest) VALUES (true,%s,%s)', (manifest['sha256'], Jsonb(manifest)))
        if conn.execute('SELECT manifest_sha256 FROM scm.legacy_import WHERE singleton = true').fetchone() != (manifest['sha256'],):
            raise MigrationError('provenance readback mismatch')
        # Sequence changes are nontransactional, but advancing is harmless on rollback.
        conn.execute("SELECT setval(pg_get_serial_sequence('scm.characters','ordinal'), COALESCE(MAX(ordinal),1), MAX(ordinal) IS NOT NULL) FROM scm.characters")
    return action

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, help='frozen offline proxy snapshot')
    parser.add_argument('--manifest', required=True, type=Path, help='outside source; created exclusively on dry-run, consumed on apply')
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--writers-stopped', action='store_true', help='operator attests all old/new proxy writers are stopped')
    parser.add_argument('--dsn-env', default='SCM_MIGRATION_DSN', help='environment variable containing PostgreSQL DSN; never pass secrets on CLI')
    args = parser.parse_args(argv)
    try:
        source = args.source.resolve()
        target = args.manifest.resolve()
        if target.is_relative_to(source): raise MigrationError('manifest must be outside source snapshot')
        if args.apply:
            if not args.writers_stopped: raise MigrationError('--apply requires --writers-stopped and an immutable snapshot')
            if not os.environ.get(args.dsn_env): raise MigrationError('PostgreSQL DSN environment variable is missing')
            expected = json.loads(target.read_text())
            verify_snapshot(source, expected)
            action = apply_postgres(os.environ[args.dsn_env], source, expected)
            print(json.dumps({'status': action, 'manifest_sha256': expected['sha256']}))
        else:
            manifest = preflight(source)
            verify_snapshot(source, manifest)
            with target.open('x', encoding='utf-8') as handle:
                json.dump(manifest, handle, indent=2, ensure_ascii=False)
                handle.write('\n')
            print(json.dumps({'status': 'dry-run', 'manifest_sha256': manifest['sha256'], 'counts': {table:len(rows) for table,rows in manifest['tables'].items()}}))
        return 0
    except (MigrationError, OSError, ValueError) as exc:
        print(f'migration refused: {exc}', file=sys.stderr)
        return 2
    except Exception:
        # Driver errors can contain credentials or row data. Never print them.
        print('migration failed; outcome unknown; keep writers stopped; verify receipt and full destination before retrying the identical manifest; no automatic retries (database error details suppressed)', file=sys.stderr)
        return 2

if __name__ == '__main__':
    raise SystemExit(main())
