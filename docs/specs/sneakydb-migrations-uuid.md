# Spec: SneakyDB migrations and UUID identity columns

## Goal

Adopt [team database migrations](https://github.com/Team-Sneakymouse/team-development-policy/blob/main/policy/database-migrations.md) for SneakyCharacterManager’s `scm` schema, then store player and character identity as PostgreSQL `uuid` instead of `text`.

## Non-goals

- Do not migrate `skin_uuid` (opaque MineSkin text).
- Do not create or drop the `scm` schema in scripts (operator creates it).
- Do not keep the legacy `template` owner or named template character ids.
- Do not change the legacy YAML / proxy storage path.

## Plugin id

`migrate` uses the existing stable id `sneakycharacters` (`SneakyCharacterManager.IDENTIFIER`).

## Scripts

| Script | Purpose |
|--------|---------|
| `V001__init.sql` | Today’s Live shape: tables from `db/001_scm.sql` plus the unique `character_id` index from `db/002_unique_character_id.sql`, without `BEGIN`/`COMMIT` or `CREATE SCHEMA`. Keeps `scm.schema_version` so an operator can mark V001 applied against an already-provisioned database. |
| `V002__uuid_identity.sql` | Delete `template` rows; retype `owners.owner_id`, `owners.last_played_character`, `characters.owner_id`, and `characters.character_id` to `uuid` (empty last-played → NULL); drop `scm.schema_version`. Non-UUID leftovers fail the script. |

## Runtime

On `storage.backend=sneakydb`, enable calls `SneakyDBProvider.getApi().migrate(IDENTIFIER, SneakyCharacterManager.class)` and does no work on `scm` tables until that future completes. On failure the backend stays inert (same as today’s failed readiness).

## Tests

- Unit tests mock `migrate` instead of `whenReady` + `schema_version`.
- PostgreSQL integration applies every `db/migrations` script to an empty `scm` schema, then exercises the store (owners/characters are UUID strings).

## Operator adoption

1. Ensure schema `scm` exists.
2. If Live already matches V001, record V001 as applied in `sneakydb.schema_migrations` before deploying a jar that calls `migrate`.
3. Deploy; V002 runs once and is a **breaking schema change** (older jars that expect text identity / `schema_version` break). Stop every server on that database before the first starts with this version.
