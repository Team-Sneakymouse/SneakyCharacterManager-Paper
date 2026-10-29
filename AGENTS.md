# Agent instructions (SneakyCharacterManager)

Paper + BungeeCord/Velocity character management for Lords of Minecraft / Team-Sneakymouse.

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
| Runtime targets | Java 25; Paper 26.2 (paperweight); BungeeCord or Velocity proxy |
| Shared Paper utils | SneakyUtils `compileOnly` on Paper module + runtime plugin depend |
| Soft deps (Paper) | PlaceholderAPI, LuckPerms, Simple Voice Chat (optional) |
| Deployment | publish via `.github/workflows/publish.yml` on the configured branch |

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

Root build produces a single shaded JAR for proxy + Paper. Proxy owns `keys.ser` generation; Paper only loads matching keys. Messaging channel: `sneakymouse:sneakycharacters`.

Guidelines:

- Keep proxy vs Paper persistence boundaries intact (identity on proxy; gameplay state on Paper).
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

## Repo-specific rules

- **Mind the module boundary.** Identity (character list, names, skins, titles.yml) is owned by the proxy (proxy-common); gameplay state (inventory, location, attributes) is owned by Paper (Character.java). A new field belongs on one side and must be synced through a new plugin-message subchannel, never by Paper reading proxy files or vice versa.
- **Change the wire protocol on both sides together.** When adding or altering a subchannel or its payload (ProxyMessageListener.java cases, ProxyServerConnection on the proxy), change both ends in one commit and keep old payloads readable, because proxy and backends are upgraded at different times from the same jar.
- **Config and plugin descriptor paths.** The Paper descriptor and default config are at paper/src/resources/paper-plugin.yml and config.yml (not src/main/resources). Edit them there, bump nothing else, and keep SneakyUtils as the required BEFORE dependency.
- **Never enable deleteCharacterDataOnServerStart.** Do not set or suggest deleteCharacterDataOnServerStart: true outside a disposable test server; it deletes every character data file on startup.
- **Character save goes through Character.save().** Use Character.save() for persistence of per-character state and keep file IO off the main thread where a path already uses the AsyncAtomicFileWriter/AtomicFiles helpers in common/; do not write character YAML with raw YamlConfiguration.save.
- **Public events are an API.** Treat LoadCharacterEvent, CharacterNickChangeEvent, CharacterSkinChangeEvent and Character getters (Character.get(Player)) as a public API used by MagicSpells (CharacterTagCondition, LoadCharacterListener) and SneakyVehicles; keep signatures source and binary compatible or update those repos in the same effort.
