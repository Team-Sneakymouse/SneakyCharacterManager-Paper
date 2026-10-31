# Backend verification and cutover gates

## Automated checks

```sh
./gradlew check build --max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m
/safe/path/scm-migration-venv/bin/python -m unittest discover -s scripts/tests -v
```

The Java PostgreSQL suite is opt-in through `SCM_TEST_JDBC_URL`, `SCM_TEST_JDBC_USER` and `SCM_TEST_JDBC_PASSWORD`; the Python suite uses `SCM_TEST_POSTGRES_DSN`. Supply these securely through the environment, targeting disposable databases only. Do not paste credentials into command lines or evidence. Without those variables the integration suites skip, so inspect JUnit XML and unittest skip output rather than treating a green task as real SQL coverage. The native-PostgreSQL checks require a fresh disposable database per suite; they are not deployment or live gameplay tests.

## Compatibility gate: before migration

The existing Velocity installation must keep using its YAML files and signed plugin messages. Updating the JAR is not permission to select the database backend or run migration.

On an isolated copy of the existing network, with **no SneakyDB installed** and the old config unchanged:

- Start Velocity and Paper with their existing matching `keys.ser`; verify plugin enable and no SneakyDB/classloading errors.
- Join as an existing player; verify last-played character, nickname, pronouns, title, skin, local location/inventory and optional attributes.
- Open `/char`, switch characters and select by a name prefix; confirm enabled filtering and original character order.
- Create a character, change its name/gender/tags/title/skin, disable it, reconnect and restart; confirm persistence in the original proxy files.
- Exercise the console command `chartemp`, named templates, default-skin reset, uniform application and `/userify`.
- Exercise `/titlegrant`: creating a new code and reusing an existing code must retain the canonical display.
- Repeat transport smoke checks on Bungee. Both transports continue to use the shared proxy core.
- Compare the config and data directory before/after: no automatic SQL opt-in, imported data or changes to key ownership.

Automated tests and a built JAR do not establish these in-game results. Record the exact proxy/Paper/plugin versions and observations when these checks are performed.

## Database integration gate

Use a separately provisioned disposable PostgreSQL database, never the live migration target, for tests. Apply the repository's operator DDL. Test the same operations as above on Paper instances explicitly configured for the database backend. No SCM proxy plugin or `keys.ser` should be needed in this mode.

In addition, exercise:

- Missing SneakyDB, missing/wrong schema and failed readiness: clear failure, no YAML fallback or replacement/default characters.
- Concurrent first joins/character creation, repeated join requests, and conflicting edits from two Paper instances: no duplicate default characters or lost unrelated edits.
- Rapid character selections followed by a delayed earlier response; quit/rejoin while queries are pending; plugin shutdown while reads/writes are pending: stale completions must not affect the new session.
- Database outage and recovery, worker saturation, and an uncertain write response: no blind retry of non-idempotent creation and no false persistence-success claim.
- Title/skin/cache refresh across instances and subsequent rejoin/menu refresh; verify the documented freshness contract rather than assuming database persistence broadcasts gameplay changes.
- Backend hopping (one active backend per player): visit B, leave, change the character skin on A, wait for its database write to complete, then return to B. B must use the persisted skin, not its previous visit's local skin history. Repeat B → A after another change. In SneakyDB mode skin undo/uniform history starts fresh on each backend join; within the current backend session, uniforms and character reloads still restore the selected session skin. Legacy proxy-mode history behavior is unchanged.
- Reload: the selected storage authority must not silently change at runtime.
- Verify the combined JAR retains Paper, Bungee and Velocity descriptors, while excluding SneakyDB API/implementation classes.

## Migration gate

Follow [migration.md](migration.md). Dry-run and inspect the reviewed manifest on a frozen snapshot. Verify all fields, disabled records, template IDs, insertion order, references, cache collisions and source checksums. Run the importer integration test against a fresh disposable database before any production consideration.

Actual cutover is a separate, operator-approved action: stop writers, back up, import, independently validate, then explicitly select database mode across the intended Paper instances. Avoid simultaneous legacy YAML and SQL authorities for one logical dataset.

## Rollback

Before database opt-in, rollback is the existing JAR/config and untouched legacy data. After SQL-mode writes, the old YAML snapshot is stale: stop writers and reconcile/export SQL changes or restore an approved backup before choosing a legacy rollback. Merely switching the backend back loses post-cutover changes.
