# SneakyCharacterManager

Character management system for Paper + BungeeCord/Velocity networks.

This plugin lets players maintain multiple roleplay characters while syncing character identity over BungeeCord or Velocity and character gameplay state on Paper.

## Project layout

- `paper/`: gameplay-side plugin (commands, nicknames, skins, placeholders, local character state files).
- `proxy-common/`: shared proxy core (platform-agnostic logic, YAML persistence, RSA signing, character data, skin caching).
- `bungee/`: BungeeCord adapter (thin wrapper that delegates to `proxy-common`).
- `velocity/`: Velocity adapter (thin wrapper that delegates to `proxy-common`).
- root project: build, publish, signing, and release tasks. Produces a single JAR containing all modules.

## Runtime requirements

- Java 21
- Paper server(s) (paperweight/dev setup targets 1.21.4)
- BungeeCord or Velocity proxy
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

## Data model (high-level)

- Proxy stores identity metadata in:
  - `plugins/SneakyCharacterManager/characterdata/<playerUUID>.yml`
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

## Commands (Paper)

### Player commands

- `/char` (opens menu)
- `/char <name...>` (select by name)
- `/char confirm` (confirm character deletion flow)
- `/nick <name...>`
- `/skin <url> [slim|classic]` (set skin from URL)
- `/skin revert` (revert to Mojang default skin)
- `/skin fetch [player]` (get a player's skin URL)
- `/skin state <id>` (apply a previous skin state from this session)
- `/names <on|off|character>`
- `/chargender <masculine|feminine|nonbinary|clear>`

### Admin commands

- `/charadmin`
- `/chartag`
- `/charprefix`
- `/charscan`
- `/uniform`
- `/savetemplatechar`
- `/userify`
- `/migrateinventories`
- `/skinqueue <status|flush|detail <priority>>`

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
- Central preflight: `./gradlew validateCentralConfig`
- Central publish task: `./gradlew releaseToCentral`

---

## Publishing to Maven Central

This repository is configured to publish:

- `io.github.team-sneakymouse:sneakycharactermanager-paper:<version>`
- `io.github.team-sneakymouse:sneakycharactermanager-bungee:<version>`

The single JAR produced by `./gradlew build` bundles Paper, BungeeCord, Velocity, and proxy-common modules together and can be installed on any supported platform.

### 1) Credentials and signing setup

Add credentials in either:

- user-level `~/.gradle/gradle.properties` (recommended), or
- project-local `./.gradle/gradle.properties` (gitignored local file)

Example:

```properties
sonatypeUsername=<CENTRAL_PORTAL_TOKEN_USERNAME>
sonatypePassword=<CENTRAL_PORTAL_TOKEN_PASSWORD>

# Required for non-SNAPSHOT releases
signingKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----
signingPassword=<GPG_PASSPHRASE>
```

Notes:

- Use **Central Portal user token** credentials, not your account email/password.
- For release versions, your signing public key must be published to supported keyservers.

### 2) Snapshot publish

Default project version is `1.0-SNAPSHOT`.

```bash
./gradlew releaseToCentral
```

### 3) Release publish (example: tag `v1.6.0`)

```bash
./gradlew releaseToCentral -PreleaseVersion=1.6.0 --rerun-tasks
```

`--rerun-tasks` is recommended for release retries to ensure fresh signature artifacts.

### 4) Common release troubleshooting

- `401 Unauthorized`: wrong token username/password.
- `403 Forbidden` on snapshots: snapshot publishing not enabled for namespace.
- `Could not find a public key by the key fingerprint`: upload signing public key to keyservers, wait for propagation, then rerun release.

---

## Consuming published artifacts

### Gradle Kotlin DSL (`build.gradle.kts`)

Release:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    compileOnly("io.github.team-sneakymouse:sneakycharactermanager-paper:1.6.0")
    // or bungee module:
    // compileOnly("io.github.team-sneakymouse:sneakycharactermanager-bungee:1.6.0")
}
```

Snapshot:

```kotlin
repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}

dependencies {
    compileOnly("io.github.team-sneakymouse:sneakycharactermanager-paper:1.0-SNAPSHOT")
}
```

### Gradle Groovy DSL (`build.gradle`)

Release:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    compileOnly "io.github.team-sneakymouse:sneakycharactermanager-paper:1.6.0"
    // compileOnly "io.github.team-sneakymouse:sneakycharactermanager-bungee:1.6.0"
}
```

Snapshot:

```groovy
repositories {
    mavenCentral()
    maven { url "https://central.sonatype.com/repository/maven-snapshots/" }
}

dependencies {
    compileOnly "io.github.team-sneakymouse:sneakycharactermanager-paper:1.0-SNAPSHOT"
}
```

### Maven (`pom.xml`)

Release:

```xml
<dependencies>
  <dependency>
    <groupId>io.github.team-sneakymouse</groupId>
    <artifactId>sneakycharactermanager-paper</artifactId>
    <version>1.6.0</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

Snapshot:

```xml
<repositories>
  <repository>
    <id>sonatype-central-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>io.github.team-sneakymouse</groupId>
    <artifactId>sneakycharactermanager-paper</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```