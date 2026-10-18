# Agent instructions (SneakyCharacterManager)

Paper + BungeeCord/Velocity character management for Lords of Minecraft / Team-Sneakymouse.

<!-- BEGIN TEAM-POLICY MANAGED BOOTSTRAP -->
This project is enrolled in Team-Sneakymouse development policy. Before implementing, or when authorization or risk is unclear, read `.team-policy/manifest.json` and `.team-policy/policy/core.md` from this project root. Read `.team-policy/policy/scope-and-testing.md` when classifying risk or changing behavior. Read `.team-policy/workflows/development.md` for nontrivial multi-step work. Read `.team-policy/policy/sneakyutils.md` when adding or sharing Paper util/manager code. Report a missing payload or a manifest/payload revision mismatch rather than substituting remembered rules. Load optional skills from `.team-policy/skills/` only when relevant. Follow local build/architecture facts and explicit exceptions below. When host instructions and team policy repeat the same norm, follow the host wording. If they require incompatible actions, surface the conflict before expanding work. Distinguish running checks from implementing tests; a source edit does not automatically require a new test.
<!-- END TEAM-POLICY MANAGED BOOTSTRAP -->

## Project facts

| | |
|--|--|
| Project | SneakyCharacterManager-Paper |
| Shared policy | revision in `.team-policy/manifest.json` |
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
| Local Paper server | `./gradlew runServer` |
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
