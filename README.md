# SneakyCharacterManager

Character management system for Paper + BungeeCord/Velocity networks.

This plugin lets players maintain multiple roleplay characters while syncing character identity over BungeeCord or Velocity and character gameplay state on Paper.

- **[AGENTS.md](./AGENTS.md)** — shared instructions for LLM coding agents (Cursor, Claude Code, Codex, etc.)

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

Database mode requires the operator-applied [schema contract](db/001_scm.sql), followed by [`db/002_unique_character_id.sql`](db/002_unique_character_id.sql). Importing legacy data is a separate, operator-run Python tool: see [offline migration and rollback](docs/migration.md). There is no automatic migration or YAML fallback on database failure. Do not enable SQL writers while legacy YAML writers remain authoritative for the same dataset.

Database mode reads authoritative character snapshots for join, list/menu and selection operations. Titles refresh on join and title creation; already-observed title codes are retained against out-of-order snapshots. There is no push subscription, periodic cross-server broadcast, or automatic update to already-loaded player appearances. Changes made elsewhere become visible at those read boundaries; editing/removing existing title displays directly in SQL is not a supported live refresh workflow.

See [backend verification and cutover gates](docs/backend-verification.md) for legacy no-SneakyDB smoke checks, database concurrency/failure checks and the manual cutover checklist. No production migration, backend switch or deployment is performed by building or opening the feature PR.

## Data model (high-level)

- Proxy stores identity metadata in:
  - `plugins/SneakyCharacterManager/characterdata/<playerUUID>.yml`
  - `plugins/SneakyCharacterManager/titles.yml` (title key → display text catalog; synced to backends)
- Paper stores per-character gameplay state in:
  - `plugins/SneakyCharacterManager/characterdata/<ownerUUID>/<characterUUID>.yml`
- Messaging channel between proxy and backend:
  - `sneakymouse:sneakycharacters`
- Character IDs are unique across all accounts. New characters get random UUIDs. In database mode, `db/002_unique_character_id.sql` enforces this with a unique index. Template characters were removed; rows stored under the legacy `template` owner stay in place and keep their IDs reserved.

## Character identity

A character is identified by its owner and its character ID, `CharacterKey(owner, characterId)` in `net.sneakycharactermanager.paper.api.character`. Character IDs are unique across all accounts, but everything stored about a character is filed under its owner, so the key carries both. Usually a player wears their own character, but another player can wear it too, so the player wearing a character (its *wearer*) is not its owner.

**The rule:** everything stored about a character is addressed by its key, never by its wearer:

- the gameplay file `characterdata/<owner>/<characterId>.yml`: load, save, and creating it;
- every identity edit sent as `updateCharacter` (`/nick`, `/chargender`, `/title`, `/chartag`, `/skin`, skin results, deletion);
- the skin pipeline: a skin request carries its key from request to result. Queued skin jobs are matched on URL + requester + key, and raising or dropping a job matches the key too;
- `LoadCharacterEvent`, `CharacterNickChangeEvent` and `CharacterSkinChangeEvent`, which expose `getCharacterKey()`.

The gameplay file path didn't change. Until now the owner was always the wearer, so existing files are already at `<owner>/<characterId>.yml` and no migration is needed.

**Exceptions that stay with the wearer** (the body), by design:

| State | Why |
| --- | --- |
| `Character.get(player)`: what a player is wearing | It is a fact about the body. Each save still goes to the worn character's own key. |
| Nametags, placeholders, LuckPerms tag contexts | They show whoever is in front of you, using the worn character's data. |
| Title unlock permission (shown on the plate and in the `/title` menu) | LuckPerms nodes belong to accounts. A borrowed character's title is hidden if the wearer hasn't unlocked it. This is a known gap; the equipped title itself is saved on the character. |
| Character lock permission (`sneakycharacters.character.<id>`) | Checked on the player loading the character. |
| `chardisable` / `charenable` | They lock a player out of their own characters. |
| Skin undo history (`/skin state`, [Undo]/[Re-apply]) | It is session-only and per player. Each entry names its character key, so it is only restored onto that exact character. |
| `/charprefix` | It lives in memory on the loaded character and is lost on reload, as before. |
| Skin queue requester (priority on join/quit, dropped when offline, menu repaint) | A job is applied to, and messages, the player who asked. |
| SneakyDB in-flight state per player (join, result fences) | It tracks what is on its way to a body. The owner queue and revision fences are per owner. |

**Other behaviour that follows from the rule:**

- A character with no gameplay file starts from fresh defaults (world spawn, empty inventory). The one exception is the owner's very first character on this server: it still starts from the owner's live inventory and location, as before. A character worn by someone else never copies its wearer.
- Gameplay files are created when a character is loaded, not when a character list arrives.
- A skin that resolves after its wearer switched to another character is still saved to the character it was requested for. It is only put on the body if the body still wears that character.

The storage mode is public too: `StorageMode.current()` (same package) returns `LEGACY` or `SNEAKYDB`.

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
- `skin`:
  - `resolveTimeoutSeconds`: How long to wait for a skin cache lookup (default `5`; skin URL downloads add `fetch.timeoutSeconds`)
  - `fetch`: Rules for downloading player-supplied skin URLs (`/skin <url>`, uniforms, cache hashing). Read on every fetch, so `/charreload` applies changes.
    - `allowedHosts`: Host names allowed (default `[]` = any public host). An entry starting with `.` also matches subdomains, e.g. `.discordapp.net`. `textures.minecraft.net` is always allowed
    - `allowedPorts`: Explicit ports allowed (default `[80, 443]`)
    - `maxBytes`: Download size cap (default `1048576`)
    - `maxRedirects`: Redirect hops followed, each re-checked (default `3`)
    - `timeoutSeconds`: Total time for one download, including redirects (default `5`)

  Regardless of these settings, skin URLs must be http(s) without user info, must resolve only to public addresses (no loopback, private, link-local/cloud metadata or other special ranges), and must be a 64x64 or 64x32 PNG. Paper downloads and hashes player URLs itself; the proxy only downloads `textures.minecraft.net` images.
- `imgurApiUrl`
- `imgurClientId`
- `webServerFolderPath`
- `webServerURLPrefix`
- `gender-suffixes.*`

Titles (proxy file `titles.yml`, not Paper `config.yml`):

- Stored on the proxy and pushed to each Paper backend on player join / title create
- `key: "Display Text"` — unlock with `sneakycharacters.title.<key>`; characters store the key; backends resolve display text for the second nameplate line in characters-only mode (styled via Paper `titlePrefix`)
- A stored title is not shown if its key is missing from the catalog or the player no longer has `sneakycharacters.title.<key>`. The stored key is kept, so re-adding the title or re-granting the permission shows it again. With LuckPerms, permission changes refresh the nameplate on the next tick; otherwise the next nameplate rebuild (character load, title catalog sync, respawn/teleport rebuilds) applies it
- If you previously kept `titles.yml` on Paper, copy it into the proxy plugin data folder once

## Commands (Paper)

### Player commands

- `/char` (opens menu)
- `/char <name...>` (select by name)
- `/char confirm` (confirm character deletion flow)
- `/nick` (open a native name dialog for the current character; `/nick <name...>` still works)
  - Names use the same banned-word and character checks in both paths. Without `sneakycharacters.formatnames`, names are limited to 32 characters; that permission bypasses length/format checks, never banned words.
  - The native input enforces length while typing. Other validation runs on Save and reopens the form with an explanation and the exact draft. An oversized draft is retained if formatting permission changed, but must be shortened before saving. Changing characters requires reopening `/nick`.
- `/skin <url> [slim|classic]` (set skin from a direct link to a public 64x64 or 64x32 PNG; see `skin.fetch`)
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

## Skin events (Paper API)

Both events live in `net.sneakycharactermanager.paper.handlers.character`:

- `CharacterSkinChangeEvent` remains the initial character-skin **request** hook, with a pending `SkinState` (requested URL/default and optional model, not resolved signed data).
- `CharacterSkinAppliedEvent` is a cancellable `PlayerEvent` dispatched synchronously inside `SkinUtil.applySkin` on the server thread, **before** profile preparation/application despite its name. `getProfileProperty()` returns the actual resolved `ProfileProperty`, including `getValue()` and `getSignature()`; `getSkinUrl()` decodes its texture URL (null if undecodable). It does not expose a potentially stale/pending `SkinState` or promise a character identity for every path.

The resolved hook covers every `applySkin` call: newly resolved/cached skins, character loads, uniforms, `/skin state` undo/re-apply, and default/Mojang restoration. Listeners still see the old player profile. Consume the event property directly, or schedule a next-tick read of the player's profile if the application was not cancelled/superseded; this is not a client-render acknowledgement. Keep listeners non-blocking.

Cancelling prevents all work inside `applySkin`: profile preparation (including texture removal), `setPlayerProfile`, vehicle removal and teleport. It does **not** roll back caller-side history, caches, messages or backend updates; use the initial request hook to veto the character-skin request itself. Cancellation of the resolved hook is a local application veto, not a storage transaction.

## Nameplates

### Who sees a plate

A character's nameplate is a text display riding the player. SCM shows it per viewer:

- Hiding a player from one viewer (`hideEntity`/`hidePlayer`, by any plugin) hides their plate from that viewer only; showing them again shows the plate and resends its text. The plate is not destroyed or rebuilt for anyone else.
- Dead, spectating and CMI-vanished players (`%cmi_user_vanished_symbol%` resolves to a non-empty value; an unresolved placeholder is not a vanish) have no plate at all, for every viewer including staff. SCM rebuilds it when that ends.
- A plate attached to another entity (below) follows each viewer's visibility of that entity instead of the player.

The scoreboard tag `NicknameEntity` on the display is unchanged but is not an API: do not find, move, hide or resend plates yourself.

### Attachment API (Paper)

`net.sneakycharactermanager.paper.api.nameplate` lets a plugin put a player's plate on another entity (a body copy, a shell, a disguise). The service is registered in Bukkit's `ServicesManager` at the end of SCM's enable and unregistered when SCM disables.

```java
NameplateAttachments.get().ifPresent(api -> {
    AttachResult result = api.attach(plugin, player, shell);
    if (result instanceof AttachResult.Attached attached) {
        NameplateHandle plate = attached.handle();
        plate.setBlank(burrowed);                         // blank text for everyone
        plate.setExcludedViewers(Set.of(staffUuid));      // never shown to these viewers
        plate.moveTo(otherShell);                         // same rules as attach
        plate.park();                                     // back on the player, hidden from everyone
        plate.close();                                    // back on the player, normal visibility and text
    } else if (result instanceof AttachResult.Refused refused) {
        // refused.reason(): CONFLICT, OWNER_OFFLINE, ANCHOR_INVALID, DIFFERENT_WORLD,
        // ANCHOR_IS_OWNER, CONSUMER_DISABLED, SCM_DISABLED; nothing changed
    }
});
```

- **One owner per plate, first plugin wins.** While a plugin holds an active attachment, another plugin's `attach` returns `CONFLICT`. The same plugin calling `attach` again gets the same handle, moved to the new anchor.
- **Survives rebuilds.** Nickname/title/gender changes, `/charreload`, `respawnNameTags`, same-world teleports, gamemode changes, death and vanish rebuild or remove the display; the attachment stays and every new plate goes back onto the anchor with the same blank/excluded settings. The owner needs no plate to attach.
- **Releases are terminal** (`NameplateHandle.releaseReason()`): `CLOSED`, `OWNER_QUIT`, `CONSUMER_DISABLED`, `SCM_DISABLED`, `ANCHOR_INVALID` (anchor removed, unloaded, changed world, or refused the plate as a passenger), `OWNER_CHANGED_WORLD`. Afterwards the handle is inert (mutators return `false`) and can never affect a later attachment; attach again for a new handle. There is no callback: check `isActive()` where you use the handle.
- **Threading.** Everything except `owner()`, `consumer()`, `isActive()` and `releaseReason()` must run on the server main thread and throws `IllegalStateException` otherwise.
- **Not exposed:** the display entity, packets and SCM's `Nickname` classes.

The plate display is not persistent (Paper skips non-persistent passengers when saving), so a plate on an anchor is not written into chunk data; unloading the anchor releases the attachment (`ANCHOR_INVALID`) and SCM returns the plate to the player on the next tick, rebuilding it if it unloaded with the anchor.

**Migration (consumers).** This replaces moving SCM's display entity directly. There is no compatibility path: SCM ignores the unreleased `sneakycharactermanager:keep-nameplate` metadata from PR #13, and SCM places and shows every plate itself (a plate moved by another plugin is put back on its player or anchor within a second). SneakyRacials must adopt this API in its own release, pinned to the SCM version that publishes it.

**Known limitation.** Paper refuses API cross-world teleports (`Player#teleport`) of a player carrying passengers before any event fires; an unattached plate rides the player, so this pre-existing behaviour is unchanged. Portal and respawn world changes are unaffected.

## Character directory (Paper API)

`net.sneakycharactermanager.paper.api.directory.CharacterDirectory` looks up characters whether or not they are loaded. It is read-only and registered in Bukkit's `ServicesManager` at the end of SCM's enable, in both storage modes.

```java
CharacterDirectory.get().ifPresent(directory -> {
    CompletableFuture<List<CharacterInfo>> mine = directory.charactersOf(accountUuid); // enabled, in menu order
    CompletableFuture<Optional<CharacterInfo>> one = directory.character(characterUuid); // offline, disabled included
});
// record CharacterInfo(UUID id, UUID owner, String name /* plain text */, boolean enabled)
```

- Callable from any thread. Futures complete on an arbitrary thread; hop to the main thread before touching Bukkit state.
- Every call reads the store again, so results include renames made by admins or on other servers. Nothing is cached: cache on your side for frequent callers such as tab completion.
- Only UUID ids are returned; legacy template rows and other non-UUID ids are not part of the directory.
- **SneakyDB mode:** queries `scm.characters`.
- **Legacy mode:** a plugin-message round trip to the proxy (`directoryOwner` / `directoryCharacter`), which reads its account files on a background thread. Plugin messages need a player connection, so a lookup fails right away with `IllegalStateException` while nobody is online on that server. A lookup that gets no complete reply, for example from a proxy older than this API, fails with `TimeoutException` after 5 seconds. Update the proxy together with the Paper servers.
- Renames that don't happen on this server fire no `CharacterNickChangeEvent`; look the character up instead.

### Chunked list replies (legacy wire format)

A plugin message from the proxy to a backend server is capped at 32767 bytes. List replies (directory lookups, and `/userify` search results) are sent as `listChunk(requestId, total, offset, items)` messages of at most 16 KiB of items each; Paper reassembles them by offset. A reply that cannot fit (a single field over 16 KiB) is refused with one `listError(requestId, reason)` message, and the lookup fails right away instead of timing out. Paper appends the request id to `getAllCharacters`; a proxy that receives the old payload without it still sends the original single `getAllCharacters` reply.

## SkinState undo / re-apply

The plugin tracks skin changes during a server session, allowing players to undo and re-apply skins via clickable chat buttons.

### How it works

- Every time a skin is applied to a player (via `/skin`, `/uniform`, character load, or `/skin revert`), a **SkinState** is recorded in memory. Each state captures the texture, signature, character key (owner + ID), and what the proxy believes the skin to be. Non-uniform states are labeled **Regular**; uniform states use a short label derived from the uniform map key (e.g. `police_officer` → `Police officer`), or **Uniform** when no friendly key exists (e.g. hash-only cache keys).
- When a skin changes due to `/skin` or `/uniform`, the player receives a clickable chat message:
  - **[Undo]** reverts to the previous skin state.
  - **[Re-apply]** re-applies the current state (useful after further changes).
- Players can also manually apply any recorded state with `/skin state <id>`.
- States persist across relogs within the same server session (cleared on server restart).
- When loading a character that was already used this session, the most recent skin state for that character is restored automatically instead of re-fetching.
- History is kept per player. If a skin finishes after the player switched away, it is saved to its character and that character's older states are dropped, so the next load uses the saved skin.

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
