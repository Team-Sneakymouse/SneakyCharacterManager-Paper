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
| Character slots (slot permissions, Character Crystal) | LuckPerms nodes belong to accounts. The crystal is an item in the worn character's inventory, so it only works while you wear one of your own characters, and only swaps you if you are still on the character you started it on. |
| `chardisable` / `charenable` | They lock a player out of their own characters. |
| Skin undo history (`/skin state`, [Undo]/[Re-apply]) | It is session-only and per player. Each entry names its character key, so it is only restored onto that exact character. A swap moves each character's latest entry to its new body. |
| `/charprefix` | It lives in memory on the loaded character and is lost on reload, as before. |
| Skin queue requester (priority on join/quit, dropped when offline, menu repaint) | A job is applied to, and messages, the player who asked. |
| SneakyDB in-flight state per player (join, result fences) | It tracks what is on its way to a body. The owner queue and revision fences are per owner. |

**Other behaviour that follows from the rule:**

- A character with no gameplay file starts from fresh defaults (world spawn, empty inventory). The one exception is the owner's very first character on this server: it still starts from the owner's live inventory and location, as before. A character worn by someone else never copies its wearer.
- Gameplay files are created when a character is loaded, not when a character list arrives. `/charadmin` can only edit the inventory of a character that has been loaded at least once on this server.
- A skin that resolves after its wearer switched to another character is still saved to the character it was requested for. It is only put on the body if the body still wears that character.
- The same goes for all work that finishes later (async results, menus and dialogs left open, chat buttons): it uses the character key captured when it started, and only touches the body if `Character.isWearing(body, key)` is still true. This covers cached uniforms, `/title`, and `/titlegrant`, which grants the permission but only equips the title on the character the grant started for.
- A newer skin on a character drops older queued skin jobs for it and other players' undo history for it, so nothing older can overwrite it later.
- Loading a character sees its latest save even before that save reaches the disk, whether the same player reloads it or another player picks it up.
- `/char confirm` refuses to delete a character someone is playing, and `/skin revert` on a borrowed character uses its owner's Mojang skin.

### Holder rule

At most one body wears a character. The holder registry, `CharacterHolders.holderOf(CharacterKey)` (same package), says which online body wears it. Every load checks it before touching the body, whatever path it came from. When another body (the *holder*) already wears the character:

| Load | What happens |
| --- | --- |
| The owner joining as it (their last-played character names it) | The owner wins. The holder's live state is saved, the holder is released (taken off the character and sent its own last-played character), and the owner loads the character where the holder stood. |
| The owner picking it in `/char` (menu or `/char <name>`) | Nothing loads. The handler another plugin registered with `CharacterHolders.setSelectionHandler` gets the pick. With no handler, or if it declines, the owner is told someone else is playing it. In SneakyDB mode the owner's last-played character doesn't change, also when a borrow of that character is still on its way. |
| Anything else: the internal borrow load, staff tools, another plugin calling `Character.load()`, any load onto a non-owner, forced or not | Refused. |

- A character is held from the moment its load starts, before the holder rule runs, so a load of it started from inside that load (a `LoadCharacterEvent` listener, or anything the owner-join release triggers) is refused, whichever body it targets.
- A released holder getting its own last-played character back is not a login: if a third body wears that character, the return is refused and the released holder gets its character menu.
- Creating a character gives it a new ID, so it is never held.
- A held character that its owner disables stays on its holder until the holder switches. (`/char confirm` already refuses to delete a character someone is playing.)
- Legacy proxy mode: the proxy moves last-played before Paper sees a `/char` pick, so a refused pick there still moves it. Nothing in legacy mode puts one owner's character on another body, so this only matters for SneakyDB.
- The release step on the owner's join is the same as `Puppets.release` (see "Puppets" below), and fires `PuppetLeaveEvent` with `OWNER_JOINED`.

Save-before-load: a load never reads an older gameplay file than the latest save queued for it, because loads read queued saves before the disk (see the "Other behaviour" list above). That includes the owner's join, which reads the holder's save before it reaches the disk.

The storage mode is public too: `StorageMode.current()` (same package) returns `LEGACY` or `SNEAKYDB`.

### Puppets

Another plugin can put a player's character on a different body (a *puppet*) and take it off again, through `Puppets` in the same package. A body is puppeting while it wears a character it doesn't own. SneakyDB mode only: in legacy mode `puppet` and `release` return `UNSUPPORTED`, and `Puppets.isSupported()` is false. Every method runs on the main thread, and futures complete on it.

- `Puppets.puppet(body, CharacterKey)` returns a `CompletableFuture<PuppetResult>` that always completes. It is a normal switch for the body: what it wore is saved first, then the character loads from its owner's gameplay file with its owner's name and skin. The owner may be offline. It never writes a last-played character (neither the body's nor the owner's), doesn't block commands, and isn't re-applied on rejoin. Everything saved or edited while wearing it goes to the owner (see "Character identity").
- `Puppets.release(body)` returns `RELEASED`, `NOT_PUPPETED` or `UNSUPPORTED`. It saves the borrowed character where it stands, takes it off at once, and loads the body's own last-played character as a normal switch. Until that arrives the body wears nothing and its inventory is cleared (when SCM manages inventories), so the borrowed items aren't on two bodies. If a third body wears that character by then, the body gets its character menu. A puppet still on its way isn't cancelled by `release`: release once its future completes.
- `Puppets.borrowedBy(body)` is the character the body wears if it isn't its own, or null.
- `PuppetLeaveEvent` (a `PlayerEvent` on the body) fires once whenever a puppeting body stops wearing the borrowed character, after it was saved (except in the case under `QUIT` below) and taken off. A swap is the one exception (see "Swap" below). `getCharacterKey()` is the borrowed character, and `getReason()` says how it left:

| Reason | When |
| --- | --- |
| `RELEASED` | `Puppets.release`. |
| `LOADED_OTHER` | Any other character loaded onto the body: its own `/char`, a staff load, another `puppet`. Reloading the same character doesn't count. |
| `QUIT` | The body quit. It rejoins as its own last-played character. Also fired when the autosave finds a body already offline (a missed quit, which shouldn't happen); that one isn't saved first, since an offline body can't be read, so the owner keeps the last autosave. |
| `OWNER_JOINED` | The owner joined as the character and took it back (holder rule above). |
| `LOCKED_OUT` | The console's `chardisable`. |

  SCM disabling doesn't fire it: the body keeps the character until the server stops, and its state is still saved to the owner.

`PuppetResult`:

| Result | Meaning |
| --- | --- |
| `PUPPETED` | The body wears the character now. The only success. |
| `UNSUPPORTED` | Legacy mode. |
| `BODY_OFFLINE` | The body isn't online. |
| `OWN_CHARACTER` | The body owns the character; that's a `/char` switch, not a puppet. |
| `HELD` | Another body, the owner included, wears it, when asked or by the time the load arrived. |
| `NOT_FOUND` | No such character, or it's disabled. |
| `INTERRUPTED` | Cut short, the body doesn't wear it: the owner or the body logged in or out while it was on its way, a newer load for the body replaced it, the owner's character changed while it loaded, or a listener of the load's own events (such as the previous puppet's `PuppetLeaveEvent`) took it off again at once. Trying again may work. |
| `REFUSED` | Refused when it arrived, for example a `LoadCharacterEvent` listener cancelled it. |
| `FAILED` | Storage failed, or SCM is shutting down. |

**Character lists.** `CharacterLists.of(owner)` gives any owner's characters, online or offline, as a `CompletableFuture<List<ListedCharacter>>` for another plugin's menu: key, name, skin URL, texture, signature and model, in `/char` order and without disabled characters. `ListedCharacter.current()` marks the owner's current character: the one they wear if they're online and wearing one of their own, otherwise their last-played one. It only reads: it creates nothing in storage and builds no `Character`. In legacy mode the future fails with `UnsupportedOperationException`; it also fails if storage fails or SCM shuts down. For plain names only, or to look one character up by its ID, use the [character directory](#character-directory-paper-api), which works in both storage modes.

The cases above are tested one by one in `PuppetApiTest` (its header lists them), and ticket 02's end-to-end checks run through `puppet` in `BorrowedCharacterTest`.

### Swap

`Puppets.swap(a, b)` exchanges the characters two online bodies wear, in one call on the main thread (one tick), in memory: each body takes over the other's character exactly where it stood, so onlookers see no change. Nothing is read from storage. SneakyDB mode only. It returns a `SwapResult`:

| Result | Meaning |
| --- | --- |
| `SWAPPED` | Each body wears the other's character. The only success. |
| `UNSUPPORTED` | Legacy mode. |
| `BODY_OFFLINE` | A body isn't online. |
| `SAME_BODY` | Both arguments are the same body. |
| `NOT_WEARING` | A body wears no character (for example a released puppet waiting for its own). |
| `DEAD` | A body is dead. |
| `DIFFERENT_WORLDS` | The bodies are in different worlds. The caller moves them into one world first. |
| `SLEEPING` | A body is in a bed. The caller gets it out first. |
| `BUSY` | A body or one of the two characters is in the middle of a load or another swap (for example, called from a `LoadCharacterEvent` listener). |
| `REFUSED` | Before anything was exchanged: a listener cancelled either skin's `CharacterSkinAppliedEvent`, refused or redirected a teleport, or a body quit or lost its character. Bodies that moved are put back, moving as they were. Both characters may have been saved where they stood. |
| `INTERRUPTED` | A listener of one of the swap's own events took a body off its new character, or a body quit, after the exchange. Both characters were saved before it began; each body wears whatever it wears now. |

Only `SWAPPED`, `REFUSED` and `INTERRUPTED` can change anything. The others are checked before anything happens.

**What moves, in order.** Body `a` wears X and body `b` wears Y:

1. Any open container is closed (its cursor item goes back into the inventory, as in vanilla). The player's own inventory view, with its crafting grid, stays open. `CharacterSkinAppliedEvent` fires for both skins; a veto refuses the swap here.
2. Both bodies' live state is read, and X is saved from `a` and Y from `b`. That is the one save each character gets, of exactly the state that moves.
3. **Teleport first:** both get off any vehicle, then each goes to the other's exact location and facing (no +1 Y). A teleport a listener cancels or sends elsewhere refuses the swap.
4. The holder registry and puppet bookkeeping swap in one step: `a` wears Y and `b` wears X.
5. **Items, both ways at once** (when `manageInventories` is on): each body gets the other's inventory and cursor item, which fires no event, then both 2x2 crafting grids are emptied and then filled (each fires `PrepareItemCraftEvent`). So whenever a listener runs, no item is on both bodies, and none is lost.
6. Each body gets the rest of the other's: effects, attribute base values (scale, maximum health), plugin attribute modifiers when `manageAttributes` is on (Minecraft's own modifiers, from equipment and effects, stay with the body), health, absorption, food, saturation, exhaustion, fire, freezing, fall distance, held hotbar slot, velocity, and air last. Health above the body's maximum this tick (the incoming equipment's bonus only counts on its next tick) is capped, then set again on that tick unless the body changed character or its health changed. An effect that an `EntityPotionEffectEvent` listener keeps or refuses stays as the listener decided: that is how a plugin forbids an effect on a body.
7. **Skin last:** the signed skin property each body wore moves as it is (uniforms included, nothing waits on the network); a body with no skin property hands over none. None of `SkinUtil.applySkin`'s dismount or +1 Y. Each character's latest skin-history entry moves to its new body.
8. SneakyPoses poses (sit, sleep, crawl) are put back on the body that now wears the character, at the same spot, after the skin so a sleeping pose copies the new one. A body that rode a vehicle hands its seat to the other.
9. Each nameplate stays on its body (the swap's teleports don't rebuild it) and shows the new character's name and title: sent at once to everyone tracking it, and again the next tick to anyone who starts tracking it after the teleport.
10. After that, each body that now wears **its own** character gets its last-played character set to it, through its owner's storage queue. A body wearing someone else's character keeps its pointer.

**What stays with the body:** game mode and flight, XP, ender chest, spawn point, permissions (so title unlocks and character locks), the account name that viewers with names ON see, skin-undo chat buttons, voice effect chains (SneakyShadowDancing swaps those itself), and anything another plugin keeps per player (for example MagicSpells buffs and mana). Inventory and plugin attribute modifiers also stay when SCM doesn't manage them, as for a normal load.

**Nothing can run in the middle.** Autosave, quits, commands and storage results all run on the main thread, so they can't run during the call. For the swap's own events (teleports, crafting grids, effects, air, poses, vehicles, nameplate placement), until it returns:

- no load can put anything on either body, and no load can put X or Y on any body;
- `Character.save()` does nothing for either body (step 2 already saved both), so a listener can't add a save or write a half-swapped state;
- a body kicked by a listener saves nothing more, and the result is `INTERRUPTED`;
- a body a listener takes off its new character after step 5 (a quit, `Puppets.release`) gets nothing more of it: no further effect, state, skin, pose, seat or nameplate. It is checked after every call that fires an event. The effects the body hands to the other are still all taken off, so they aren't on both bodies. Its items are where step 5 put them, so a release leaves it as a release right after the swap would: the inventory is cleared (the character's save holds it) and the cursor item and crafting grid stay. The result is `INTERRUPTED`.

So each swap writes exactly two gameplay files, X and Y, and one last-played pointer for each body that ends up in its own character.

**Events.** A swap fires no `LoadCharacterEvent`: its listeners treat it as a character switch, and MagicSpells' load spells cure effects, reset XP and scale, and add a hunger penalty, which would undo what a swap moves. It fires no `PuppetLeaveEvent` either: the plugin that swaps knows it did.

**Puppet bookkeeping afterwards.** Whatever a body wears decides it, as always. In a Shadow Dance the Driver (wearing their own X) and the Supporter (puppeting the Driver's Y) swap:

- the Driver wears Y, their own: `Puppets.borrowedBy(driver)` is null and their last-played pointer names Y;
- the Supporter wears X: `Puppets.borrowedBy(supporter)` is X, and `CharacterHolders.holderOf(X)` is the Supporter. The Supporter's pointer is unchanged;
- from then on every `PuppetLeaveEvent` for the Supporter carries X: a quit (`QUIT`), any other load (`LOADED_OTHER`), `Puppets.release` (`RELEASED`), the Driver joining as X (`OWNER_JOINED`).

**Rejoining.** The Driver rejoins as Y. If the pointer write fails, SCM's usual storage-unavailable handling applies: the Driver is told, and the Driver's storage is fenced until a restart. The pointer then still names X; if another body wears X when the Driver next joins, the owner-join rule above gives it back to the Driver.

**Poses** go through what SneakyPoses documents, since it has no API: its `%sneakyposes_sitting|sleeping|crawling%` placeholders (so PlaceholderAPI is needed), the `SneakyPosesSeat` tag on its seat, its `sit.y-offset`, `sleep.y-offset` and `sleep.camera-y-offset` settings, and the console form `/<pose> true <player> world,x,y,z,yaw`. It assumes SneakyPoses' default `end-on-teleport: true`. Without SneakyPoses or PlaceholderAPI, a posed character stands up, as on any teleport.

**Known gaps:**

- A skin change still resolving when the swap happens (`/skin`, a uniform) is saved to its character but shown only on the body that asked, so the character's new wearer shows it at its next load.
- A body that quits during the swap's own events loses the cursor item and crafting grid it was handed in step 5: SCM saves neither, and the quit's save is skipped (its inventory is in the character's step-2 save).
- What each swapping player sees on their own screen, and whether onlookers see a one-frame blink, is client behaviour that tests can't show (Shadow Dancing ticket 14).

The cases are tested in `SwapTest` (its header lists them), with `LiveStateTest`, `PoseTransferTest` and focused tests for the skin history, nameplate and skin helper.

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
