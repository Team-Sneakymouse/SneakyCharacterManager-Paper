# SneakyCharacterManager

Character management system for Paper + BungeeCord/Velocity networks.

This plugin lets players maintain multiple roleplay characters while syncing character identity over BungeeCord or Velocity and character gameplay state on Paper.

## Project layout

- `common/`: filesystem helpers shared by Paper and proxy persistence.
- `paper/`: gameplay-side plugin (commands, nicknames, skins, placeholders, local character state files).
- `proxy-common/`: shared proxy core (platform-agnostic logic, YAML persistence, RSA signing, character data, skin caching).
- `bungee/`: BungeeCord adapter (thin wrapper that delegates to `proxy-common`).
- `velocity/`: Velocity adapter (thin wrapper that delegates to `proxy-common`).
- root project: build and Maven publish tasks. Produces a single JAR containing all modules.

## Runtime requirements

- Java 25
- Paper server(s) (paperweight/dev setup targets 26.2)
- BungeeCord or Velocity proxy for the default messaging backend; alternatively, SneakyDB on Paper for explicit database mode
- SneakyDB mode requires the published API-compatible runtime `2026.10.26-fd4e29e90ad0` or a compatible newer release; no SneakyDB runtime is needed for legacy mode
- Optional soft dependencies on Paper side:
  - PlaceholderAPI
  - LuckPerms
  - Simple Voice Chat (`voicechat`)

## Network setup

1. Install the single plugin JAR on your proxy (BungeeCord or Velocity) and on each Paper backend.
2. Start the proxy first so it creates `keys.ser` in the proxy plugin data folder.
3. Copy that same `keys.ser` into each Paper plugin data folder.
   - Paper only loads keys; it does not generate them.
   - If keys do not match between proxy and backend, plugin messaging auth will fail.
   - Velocity note: the plugin overrides its data folder to `plugins/SneakyCharacterManager` (matching BungeeCord's casing) so data files are compatible when switching between proxy platforms.

## Storage backends and migration safety

The existing BungeeCord/Velocity deployment remains the default. Existing config files, proxy YAML data and matching `keys.ser` continue to be used; installing this version does not import data, change storage authority or require SneakyDB.

The opt-in SneakyDB backend runs the identity/catalog/cache operations directly from Paper through asynchronous PostgreSQL queries. It does not require SCM on the network proxy. Paper gameplay snapshots (inventories, locations and attributes) remain local files in either mode. SneakyDB manages database credentials/connections; SCM uses its public consumer API and does not bundle the manager or its API classes.

```yaml
storage:
  backend: legacy                # default, also used when the key is missing
  requestTimeoutSeconds: 15      # 1..60; database readiness/request deadline
```

Keep `storage.backend: legacy` on the current network until final migration. Only after the offline import and validation, explicitly select `sneakydb` on the intended Paper instances. Backend selection and request timeout are fixed at enable; changes require a full restart, not `/charreload`. An uncertain database operation fences further work for that owner until restart rather than automatically retrying writes. Investigate and reconcile the durable database outcome before restarting; a timeout does not prove rollback.

Database mode requires the operator-applied [schema contract](db/001_scm.sql). Importing legacy data is a separate, operator-run Python tool: see [offline migration and rollback](docs/migration.md). There is no automatic migration or YAML fallback on database failure. Do not enable SQL writers while legacy YAML writers remain authoritative for the same dataset.

Database mode reads authoritative character snapshots for join, list/menu and selection operations. Titles refresh on join and title creation; already-observed title codes are retained against out-of-order snapshots. There is no push subscription, periodic cross-server broadcast, or automatic update to already-loaded player appearances. Changes made elsewhere become visible at those read boundaries; editing/removing existing title displays directly in SQL is not a supported live refresh workflow.

See [backend verification and cutover gates](docs/backend-verification.md) for legacy no-SneakyDB smoke checks, database concurrency/failure checks and the manual cutover checklist. No production migration, backend switch or deployment is performed by building or opening the feature PR.

## Data model (high-level)

- Proxy stores identity metadata in:
  - `plugins/SneakyCharacterManager/characterdata/<playerUUID>.yml`
  - `plugins/SneakyCharacterManager/titles.yml` (title key → display text catalog; synced to backends)
- Paper stores per-character gameplay state in:
  - `plugins/SneakyCharacterManager/characterdata/<playerUUID>/<characterUUID>.yml`
- Messaging channel between proxy and backend:
  - `sneakymouse:sneakycharacters`

## Configuration (Paper)

`paper/src/resources/config.yml` currently supports:

- `deleteCharacterDataOnServerStart`
- `manageLocations`
- `manageInventories`
- `manageAttributes`
- `respawnNameTags`
- `respawnTimerSeconds`
- `see-own-nameplate`
- `titlePrefix` (MiniMessage prefix for the title nameplate line; default `<gray>`)
- `bannedWords`
- `mineskinQueueUrl` (Mineskin v2 queue endpoint)
- `mineskinSkinsUrl` (Mineskin v2 skins endpoint)
- `mineskinAuth` (Mineskin API Key)
- `mineskinUserAgent`
- `mineskin`:
  - `logging.enabled`: Toggle Mineskin API logging
  - `debug`: Detailed queue processing logs
  - `rate_limit_base`: Target hourly limit
  - `min_capacity_reservation`: Reserved slots for high-priority jobs
  - `preload_active_days`: Offline player activity cutoff for preloading
- `imgurApiUrl`
- `imgurClientId`
- `webServerFolderPath`
- `webServerURLPrefix`
- `gender-suffixes.*`

Titles (proxy file `titles.yml`, not Paper `config.yml`):

- Stored on the proxy and pushed to each Paper backend on player join / title create
- `key: "Display Text"` — unlock with `sneakycharacters.title.<key>`; characters store the key; backends resolve display text for the second nameplate line in characters-only mode (styled via Paper `titlePrefix`)
- If you previously kept `titles.yml` on Paper, copy it into the proxy plugin data folder once

## Commands (Paper)

### Player commands

- `/char` (opens menu)
- `/char <name...>` (select by name)
- `/char confirm` (confirm character deletion flow)
- `/nick` (open a native name dialog for the current character; `/nick <name...>` still works)
  - Names use the same banned-word and character checks in both paths. Without `sneakycharacters.formatnames`, names are limited to 32 characters; that permission bypasses length/format checks, never banned words.
  - The native input enforces length while typing. Other validation runs on Save and reopens the form with an explanation and the exact draft. An oversized draft is retained if formatting permission changed, but must be shortened before saving. Changing characters requires reopening `/nick`.
- `/skin <url> [slim|classic]` (set skin from URL)
- `/skin revert` (revert to Mojang default skin)
- `/skin fetch [player]` (get a player's skin URL)
- `/skin state <id>` (apply a previous skin state from this session)
- `/names <on|off|character>`
- `/chargender <masculine|feminine|nonbinary|clear>`
- `/title` (open title selection menu for the current character)

### Admin commands

- `/charadmin`
- `/chartag`
- `/charprefix`
- `/charscan`
- `/uniform`
- `/savetemplatechar`
- `/userify` (alias: `/whois`)
- `/migrateinventories`
- `/skinqueue <status|flush|detail <priority>>`
- `/titlegrant <player>` (enter display text to create or reuse a title, grant it, and equip it)
  - The key is generated automatically: lowercase, spaces/hyphens become underscores, and other characters outside `a-z`, `0-9`, and `_` are removed. Repeated underscores are collapsed and leading/trailing underscores removed.
  - If that key already exists on the proxy, its original display text is reused without modification.
- `/charreload` (reload Paper `config.yml` and refresh online nameplates)

### Console commands

- `chardisable`
- `charenable`
- `chartemp`

## Permissions

- Base groups:
  - `sneakycharacters.*`
  - `sneakycharacters.command.*`
  - `sneakycharacters.character.*`
  - `sneakycharacters.admin.*`
  - `sneakycharacters.admin.command.*`
  - `sneakycharacters.admin.bypass.*`
- Character slots:
  - `sneakycharacters.characterslots.<number>`
  - `sneakycharacters.characterslots.*`
- Other notable nodes:
  - `sneakycharacters.formatnames`
  - `sneakycharacters.skinfetch.others`
  - `sneakycharacters.title.<key>` (unlock a title from the proxy `titles.yml`)
  - `sneakycharacters.title.*`
- `sneakycharacters.character.<characterUUID>`
- `sneakycharacters.preload`: Automatic inclusion in persistent preload cache

## PlaceholderAPI placeholders

Prefix: `%sneakycharacters_<placeholder>%`

- `character_uuid`
- `character_name`
- `character_name_noformat`
- `character_skin`
- `character_slim`
- `character_tags`
- `character_hastag_<key>`
- `character_tag_<key>`
- `character_name_prefix`
- `character_gender`
- `character_gender_suffix`
- `character_pronoun_s`
- `character_pronoun_o`
- `character_pronoun_p`
- `character_pronoun_p2`
- `skinqueue_remaining`: Available hourly capacity
- `skinqueue_limit`: Total hourly capacity
- `skinqueue_reset`: Raw epoch timestamp of next reset
- `skinqueue_reset_formatted`: Readable reset timer (e.g. "45s" or "Ready")
- `skinqueue_delay`: Current inter-request delay
- `skinqueue_processing`: Active Mineskin jobs
- `skinqueue_total_queued`: Total pending jobs
- `skinqueue_queued_p<0-4>`: Pending jobs per priority tier

## SkinState undo / re-apply

The plugin tracks skin changes during a server session, allowing players to undo and re-apply skins via clickable chat buttons.

### How it works

- Every time a skin is applied to a player (via `/skin`, `/uniform`, character load, or `/skin revert`), a **SkinState** is recorded in memory. Each state captures the texture, signature, character UUID, and what the proxy believes the skin to be. Non-uniform states are labeled **Regular**; uniform states use a short label derived from the uniform map key (e.g. `police_officer` → `Police officer`), or **Uniform** when no friendly key exists (e.g. hash-only cache keys).
- When a skin changes due to `/skin` or `/uniform`, the player receives a clickable chat message:
  - **[Undo]** reverts to the previous skin state.
  - **[Re-apply]** re-applies the current state (useful after further changes).
- Players can also manually apply any recorded state with `/skin state <id>`.
- States persist across relogs within the same server session (cleared on server restart).
- When loading a character that was already used this session, the most recent skin state for that character is restored automatically instead of re-fetching.

### Proxy awareness

When `/skin state` changes the skin to one with different proxy-side values (URL, texture, signature), an `updateCharacter` message is sent to keep the proxy's character data in sync. Uniform states carry forward the previous non-uniform proxy values so the proxy never sees uniform overlay data.

## Build tasks

- Build all: `./gradlew build`
- Local publish: `./gradlew publishToMavenLocal`
- Remote publish: `./gradlew publishAllPublicationsToSneakyrpRepository`

Versions are derived from git as `yyyy.MM.dd-<12-char-hash>` (UTC commit date + abbreviated SHA).

The combined plugin JAR is `SneakyCharacterManager-<version>.jar` and can be installed on Paper, BungeeCord, or Velocity.

---

## Publishing to sneakyrp Maven

Pushes to `main` run [`.github/workflows/publish.yml`](.github/workflows/publish.yml), which builds and publishes to `https://maven.sneakyrp.com/releases`.

Published coordinates (`io.github.team-sneakymouse`):

- `sneakycharactermanager` — combined plugin JAR
- `sneakycharactermanager-common`
- `sneakycharactermanager-paper`
- `sneakycharactermanager-proxy-common`
- `sneakycharactermanager-bungee`
- `sneakycharactermanager-velocity`

The combined JAR's POM sets `sneakyrp.deployment.type` to `paper-plugin,velocity-plugin`, so Mother Mouse's `/plugins update` offers it for both Paper servers and the Velocity proxy. The module JARs have no deployment property and are not installed as plugins.

Local publish credentials (`~/.gradle/gradle.properties` or env via `ORG_GRADLE_PROJECT_*`):

```properties
sneakyrpUsername=<username>
sneakyrpPassword=<password>
```

---

## Consuming published artifacts

```kotlin
repositories {
    mavenCentral()
    maven("https://maven.sneakyrp.com/releases")
}

dependencies {
    compileOnly("io.github.team-sneakymouse:sneakycharactermanager-paper:<version>")
    // or: sneakycharactermanager-velocity / sneakycharactermanager-bungee
}
```
