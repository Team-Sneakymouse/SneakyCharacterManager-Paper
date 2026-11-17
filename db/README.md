# SCM database schema

Schema is owned by the Paper plugin’s SneakyDB migrations:

- [`paper/src/resources/db/migrations/V001__init.sql`](../paper/src/resources/db/migrations/V001__init.sql) — tables as previously applied by operators
- [`paper/src/resources/db/migrations/V002__uuid_identity.sql`](../paper/src/resources/db/migrations/V002__uuid_identity.sql) — identity columns as `uuid`; template owner removed

The operator creates schema `scm` (scripts never create schemas). On `storage.backend=sneakydb`, the plugin calls `migrate("sneakycharacters", …)` and waits for that future before touching tables.

Adoption and the breaking notice for V002 are in [docs/specs/sneakydb-migrations-uuid.md](../docs/specs/sneakydb-migrations-uuid.md).

The former operator files `001_scm.sql` and `002_unique_character_id.sql` are superseded by V001; do not apply them on new databases.
