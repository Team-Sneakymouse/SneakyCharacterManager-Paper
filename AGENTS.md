# Agent instructions (Team Sneakymouse / Lords of Minecraft)

This repo is **SneakyCharacterManager** (Paper + BungeeCord/Velocity character management). All coding agents (Cursor, Claude Code, Codex, Antigravity, Hermes, etc.) should follow this file.

<!-- BEGIN TEAM-POLICY MANAGED BOOTSTRAP -->
This project is enrolled in Team-Sneakymouse development policy via a sibling checkout (see `.team-policy/pointer.json`).

Resolve the policy root: try each `local_candidates` path in the pointer relative to this project root. If none exist, `git clone` the pointer `remote` into the first candidate using the developer's existing Git credentials. If a checkout exists but is behind `origin/<track_branch>`, `git fetch` and fast-forward that branch when safe; if local edits block the update, report and ask.

Before implementing, review `<policy-root>/README.md` as the current policy index and load the linked guidance relevant to the task. At minimum, when authorization or risk is unclear, read `<policy-root>/policy/core.md`; read `<policy-root>/policy/scope-and-testing.md` when classifying risk or changing behavior; and read `<policy-root>/workflows/development.md` for nontrivial multi-step work. For substantial Paper features, persistent world state, or changes spanning components, read `<policy-root>/policy/paper-plugin-maintainability.md`. Read `<policy-root>/policy/sneakyutils.md` when adding or sharing Paper util/manager code. Load optional skills from `<policy-root>/skills/` only when relevant.

Do not substitute remembered rules, a web mirror, or an unlisted path. Follow local build/architecture facts and explicit exceptions below. When host instructions and team policy repeat the same norm, follow the host wording. If they require incompatible actions, surface the conflict before expanding work. Distinguish running checks from implementing tests; a source edit does not automatically require a new test.
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

## Definition of done

- [ ] Scoped change only; unrelated modules untouched
- [ ] Relevant `./gradlew` tests/checks run for touched modules
- [ ] No secrets committed
- [ ] Plugin metadata updated if identity/deps changed

## Human docs

- `README.md` — network setup, commands, data model, config
