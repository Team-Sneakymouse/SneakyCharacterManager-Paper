# Offline proxy YAML migration

This standalone operator tool is never invoked by SCM or SneakyDB. Keep the existing Velocity deployment unchanged until a separately approved **manual** cutover. Do not run against production as a test.

## Freeze and review

1. Stop every legacy proxy writer. Keep all prospective database writers stopped throughout import and validation. Take a protected backup and a separate immutable snapshot of the **proxy** data directory. The tool cannot prove writers are stopped: `--writers-stopped` is an operator attestation, not a lock on another process.
2. Use only the proxy snapshot, not Paper's nested `characterdata/<owner>/<character>.yml` tree. The tool reads direct `characterdata/*.yml`, optional `skin_cache.yml`, `uniform_skin_cache.yml`, and `titles.yml`. It never opens or decodes `keys.ser`, Paper inventory/location/attributes, uniform PNGs, or configs. Nested directories and symlinks inside characterdata are refused.
3. Install Python 3.10+ and explicit dependencies in an isolated environment:

   ```sh
   python3 -m venv /safe/path/scm-migration-venv
   /safe/path/scm-migration-venv/bin/python -m pip install -r scripts/requirements-migration.txt
   /safe/path/scm-migration-venv/bin/python scripts/legacy_migration.py /safe/path/frozen-proxy --manifest /safe/path/scm-manifest.json
   ```

   Dry-run is the default and never connects to a database. The manifest must be outside the source, is created exclusively (never overwritten), and contains absolute source path, per-file SHA-256/size, every target field, and a deterministic manifest digest. Protect it as player data. Review the full manifest, not only row totals. Keep the snapshot at that exact path for apply. Source files are never written.

## Validation rules

Malformed YAML, duplicate keys including inherited merge overrides, invalid shapes, ambiguous cache/title collisions, and noncanonical owner UUIDs (except literal `template`) fail closed. IDs are not repaired. Character IDs remain arbitrary text (templates use named IDs); skin IDs and character/uniform skin UUID fields are opaque MineSkin text, not canonical owner UUIDs, and string values are preserved exactly. Common YAML 1.1 null/boolean/integer typing follows the proxy's SnakeYAML 2.2 contract: unquoted `001` becomes integer 1 and therefore string `1`, `yes` becomes Boolean true, while quoted `001` and `null` remain strings. Floating-point values and timestamps are explicitly unsupported and refused (Java formatting/timezone semantics are not assumed); non-string mapping keys and collection-to-string fields are also refused rather than silently converted. This is a deliberately restricted importer, not a complete SnakeYAML replacement. Do not rewrite the frozen source merely to bypass a refusal; investigate against the actual legacy runtime and review a new snapshot/manifest separately.

All characters, including disabled entries, are retained in insertion order through explicit global ordinals. Defaults match proxy runtime fields: enabled true, slim false, missing strings empty; legacy tag lists become comma-joined Java scalar text (null elements become literal `null`, booleans lowercase, integers decimal); Boolean fields retain Boolean values, parse string `true` case-insensitively, and otherwise use their defaults; recognized gender is case-insensitive, unknown gender becomes unset. Both `skinId` and inline skin fields remain present when both existed; runtime ID precedence is unchanged. Missing skin/title/last-played references are accepted, not repaired. Character skin URLs and uniform base URLs are exact; only global cache URLs beginning exactly `https://textures.minecraft.net/texture/` switch to `http://`. Global URL index collisions, duplicate IDs, uniform base/hash collisions, and normalized title-code collisions are errors even if repeated values are identical. Title codes follow `TitleKeys`; display text is Java-trimmed. As in `TitleCatalog`, null/empty displays and empty normalized codes are skipped before collision checking. Every skipped entry is recorded in reviewed manifest `diagnostics` (source, key, reason); the complete original titles file remains in the frozen snapshot and source hashes.

## Explicit PostgreSQL apply

Apply `db/001_scm.sql` manually to a new, empty, disposable database first. The importer checks schema version 1, never creates the application schema, and has no SQLite or plugin-lifecycle path. Use PostgreSQL connection settings for the exact database SneakyDB will use. Supply a DSN through an environment variable using your secret-management workflow; never put credentials on command lines or in manifests.

```sh
# SCM_MIGRATION_DSN must already be populated securely.
/safe/path/scm-migration-venv/bin/python scripts/legacy_migration.py /safe/path/frozen-proxy --manifest /safe/path/scm-manifest.json --apply --writers-stopped
```

Apply re-parses and compares the frozen snapshot and reviewed manifest before connection and again before commit. It uses one serializable PostgreSQL transaction and exclusive locks on SCM data tables, inserts parameterized values, reads back every field in every target table, then records provenance in `scm.legacy_import`. That receipt table is the importer's only extra schema object and is created transactionally by explicit apply (requires CREATE permission on schema `scm`). Its singleton receipt stores the complete manifest, hash, and database import timestamp. The character identity sequence is advanced above the imported ordinals; PostgreSQL sequence advancement itself is nontransactional, so rollback can leave a harmless sequence gap.

The exact same manifest plus unchanged **entire** destination is an idempotent no-op. Different provenance, changed fields/revisions, added/deleted rows, or a nonempty destination without matching provenance are refused; the tool never upserts or overwrites a changed destination. Confirmed pre-commit failures abort the transaction. A lost response during COMMIT may mean it committed: generic database failures report **outcome unknown**, never promise rollback. Database exception text is suppressed to avoid leaking credentials/player rows; inspect protected server logs if necessary. No database-success claim is produced on connection failure.

Do not start either old or new proxy writers until independently checking row counts, representative fields, disabled/template characters, selection order, skins and titles. Back up the imported database before manual opt-in. This tool does not switch configuration, deploy plugins, or migrate RSA keys. Preserve matching keys through a separate secure operator process.

## Verification and rollback

```sh
/safe/path/scm-migration-venv/bin/python -m unittest discover -s scripts/tests -v
```

The PostgreSQL integration test is skipped unless `SCM_TEST_POSTGRES_DSN` names an explicitly disposable database **without** schema `scm`. It applies the real SQL file and leaves the test schema/data in that disposable database for inspection. It checks insert, field readback, repeat no-op, and refusal after destination drift. Never point this test at a live or shared database. Unit/preflight tests are not evidence that PostgreSQL import succeeded; run this integration gate before production consideration.

On confirmed pre-commit failure, application rows and provenance roll back. On an uncertain COMMIT/connection outcome, keep all writers stopped, preserve the exact snapshot and manifest, and independently read `scm.legacy_import` plus every destination field/table before any retry. Retry only the identical reviewed manifest after verification; matching receipt and complete unchanged destination produce a no-op, while drift is refused. There are no automatic retries. Do not run concurrent schema migrations/DDL during import. On post-commit validation failure, leave both proxy writers stopped, preserve evidence, and restore the database backup or discard only the newly provisioned migration database under separate operator authorization. Source snapshot/legacy files remain untouched. Restarting the old backend is a separate manual rollback decision; never run old and new writers concurrently.
