# Agent instructions (Team Sneakymouse / Lords of Minecraft)

This repo is **SneakyCharacterManager** (Paper + BungeeCord/Velocity character management). All coding agents (Cursor, Claude Code, Codex, Antigravity, Hermes, etc.) should follow this file.

<!-- BEGIN TEAM-POLICY MANAGED BOOTSTRAP -->
This project follows the Team-Sneakymouse development policy, kept in a sibling checkout that `.team-policy/pointer.json` names.

The policy root is the first `local_candidates` path in the pointer, resolved from this project's root, that exists. If none exists, clone the pointer's `remote` into the first candidate with the developer's Git credentials and say so. Before implementation work (not for read-only investigation), run `git fetch` in the checkout, then fast-forward it to `origin/<track_branch>` if it is behind and clean; if the fetch fails or local changes block the fast-forward, report it and ask.

Before implementing, read `<policy-root>/README.md`. It is the policy index and says which documents apply to which kind of change. If the policy root can't be resolved or read, stop and report that instead of working from memory or another copy.

Build commands, architecture facts and recorded local exceptions in this file are authoritative for this repository. Where this file and the policy require incompatible actions, say so before expanding the work. Running checks and writing tests are separate decisions: a source edit does not by itself require a new test.
<!-- END TEAM-POLICY MANAGED BOOTSTRAP -->

## Project facts

| | |
|--|--|
| Project | SneakyCharacterManager-Paper |
| Shared policy | sibling checkout via `.team-policy/pointer.json` → `team-development-policy` |
| Runtime targets | Java 25; Paper 26.2 (paperweight); BungeeCord/Velocity by default, opt-in SneakyDB query backend |
| Shared Paper utils | SneakyUtils `compileOnly` on Paper module + runtime plugin depend (see team policy `sneakyutils.md`) |
| Soft deps (Paper) | PlaceholderAPI, LuckPerms, Simple Voice Chat (optional) |
| Deployment | publish via `.github/workflows/publish.yml` on the configured branch |

Custom item look, CraftEngine/pack model id, server resource pack, or ItemDisplay place/orient → read `<policy-root>/policy/paper-items.md`.

### Local exceptions

None recorded. Shared proportional testing applies. Prefer extending existing JUnit tests under `common/`, `proxy-common/`, `paper/`, and `velocity/` when behavior is new or coverage is thin; ask before adding a substantial new harness.

## Commands

| Task | Command |
|------|---------|
| Build + checks | `./gradlew check` |
| Unit tests | `./gradlew test` |
| Local Paper server | `./gradlew runServer` (stages pinned SneakyUtils into `run/plugins`) |
| Local BungeeCord | `./gradlew runBungee` |
| Local Velocity | `./gradlew runVelocity` |
| Publish (CI) | root publish tasks / `publish.yml` (Maven credentials required) |

## Architecture

```text
common/         filesystem helpers shared by Paper and proxy persistence
paper/          gameplay plugin (commands, nicknames, skins, placeholders, character state)
proxy-common/   platform-agnostic proxy core (YAML persistence, RSA signing, skins)
bungee/         thin BungeeCord adapter → proxy-common
velocity/       thin Velocity adapter → proxy-common
```

Root build produces a single shaded JAR for proxy + Paper. The default proxy backend retains `keys.ser` generation on the proxy and matching keys on Paper. Messaging channel: `sneakymouse:sneakycharacters`. SneakyDB mode is an explicit Paper opt-in; keep its API compile-only, authenticate through the manager, and preserve legacy startup without SneakyDB. Storage-authority changes require a restart; config reload must not silently change backends.

Operator SQL lives in `db/001_scm.sql`; the standalone importer and its tests live under `scripts/`. Neither schema installation nor legacy import belongs in plugin startup. Read `docs/migration.md` and `docs/backend-verification.md` before storage/migration work. Preserve the default Velocity path until a separate manual cutover; never switch deployed config or import live data as part of building/testing.

Guidelines:

- Keep identity persistence on the selected backend (legacy proxy or SneakyDB); inventories, locations and attributes remain Paper-local gameplay state.
- Prefer existing tests next to changed modules; do not invent a second persistence stack.
- Prefer SneakyUtils APIs over new shared util copies (see team policy `sneakyutils.md`).
- Secrets (`mineskinAuth`, API keys, `keys.ser`) must never appear in commits or agent evidence.
