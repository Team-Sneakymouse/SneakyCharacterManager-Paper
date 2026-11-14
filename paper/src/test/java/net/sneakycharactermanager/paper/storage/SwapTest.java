package net.sneakycharactermanager.paper.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.sneakyutils.util.InventoryUtility;
import com.sneakyutils.util.PlaceholderUtil;

import net.kyori.adventure.text.Component;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterHolders;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.api.character.PuppetLeaveEvent;
import net.sneakycharactermanager.paper.api.character.PuppetLeaveEvent.Reason;
import net.sneakycharactermanager.paper.api.character.PuppetResult;
import net.sneakycharactermanager.paper.api.character.ReleaseResult;
import net.sneakycharactermanager.paper.api.character.Puppets;
import net.sneakycharactermanager.paper.api.character.StorageMode;
import net.sneakycharactermanager.paper.api.character.SwapResult;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu;
import net.sneakycharactermanager.paper.handlers.character.LiveState;
import net.sneakycharactermanager.paper.handlers.character.LoadCharacterEvent;
import net.sneakycharactermanager.paper.handlers.character.LoadReason;
import net.sneakycharactermanager.paper.handlers.nametags.NametagManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinCache;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.handlers.skins.SkinStateManager;
import net.sneakycharactermanager.paper.handlers.titles.TitleRegistry;
import net.sneakycharactermanager.paper.listeners.ConnectionEventListeners;
import net.sneakycharactermanager.paper.listeners.TeleportEvents;
import net.sneakycharactermanager.paper.util.SkinUtil;

/**
 * {@link Puppets#swap} end to end on the SneakyDB path, as Shadow Dancing uses it: the Driver wears their own
 * character "main" (X), the Supporter puppets the Driver's "second" (Y), and the swap puts Y on the Driver and X on the
 * Supporter. Bodies are mocks whose getters and setters share real state, so each moved value can be checked. The
 * README's "Swap" section has the same lists.
 *
 * <p>State that moves, and the order (W1-W4):
 * <ol>
 * <li>W1 Each body wears the other's character: holder registry, puppet bookkeeping ({@code borrowedBy}), the
 *     character's wearer. No {@code PuppetLeaveEvent} and no {@code LoadCharacterEvent}.</li>
 * <li>W2 Each body stands exactly where the other stood (facing included) with its live state: velocity, fall distance,
 *     health, absorption, food, saturation, exhaustion, effects, fire, freezing, air, inventory, cursor item,
 *     crafting grid, held slot, attribute base values and the worn skin property. An open container is closed first.
 *     Plugin attribute modifiers move when SCM manages attributes ({@code LiveStateTest}).</li>
 * <li>W3 Teleport first, skin last; the nameplate is re-texted in place (the swap's teleports don't rebuild it), the
 *     skin history moves with each character, poses go back on after the skin and riders are re-seated.</li>
 * <li>W4 Inventory, cursor and grid stay with the body when SCM doesn't manage inventories; plugin attribute modifiers
 *     move only when it manages attributes.</li>
 * </ol>
 * Invariants (I1-I5):
 * <ol>
 * <li>I1 No save in the middle: a save from a listener during the swap writes nothing.</li>
 * <li>I2 No load in the middle: loads onto either body, or of either character onto any body, are refused; so is a
 *     nested swap ({@code BUSY}), and a swap from inside a load.</li>
 * <li>I3 No quit or release in the middle: a body kicked by a listener mid-swap saves nothing more; a release asked
 *     for mid-swap waits until the swap is done, then saves and takes off as usual, so nothing the swap moved is lost;
 *     a body locked out mid-swap (grid, effect, pose events) gets nothing more of its new character. No item is ever
 *     on both bodies or lost: the cursor and grid go into the inventory before the save, so a kick at any step
 *     leaves each item on its character's wearer, in its character's save, or dropped where vanilla drops it.</li>
 * <li>I4 One save per character: exactly two gameplay writes, X from the Driver and Y from the Supporter, both before
 *     anything moves.</li>
 * <li>I5 Last-played: the Driver's names Y, through the owner queue; the Supporter's is untouched; a stale load of the
 *     Driver still on its way is dropped. A failed write is handled as a storage failure, and the Driver's rejoin is
 *     the owner-join takeover.</li>
 * </ol>
 * Refused before anything moves (R1-R3): legacy mode, the same body, an offline body, a body wearing nothing, a dead,
 * sleeping or other-world body, off the main thread; a refused teleport puts both back ({@code REFUSED}).
 */
class SwapTest {

    @TempDir Path data;
    final UUID driverId = UUID.randomUUID();
    final UUID supporterId = UUID.randomUUID();
    final UUID thirdId = UUID.randomUUID();
    final CharacterKey main = new CharacterKey(driverId, "main");
    final CharacterKey second = new CharacterKey(driverId, "second");
    final CharacterKey spare = new CharacterKey(driverId, "spare");
    final CharacterKey supportersOwn = new CharacterKey(supporterId, "own");
    final CharacterKey thirdsOwn = new CharacterKey(thirdId, "third");
    final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
    final Store api = new Store();
    final YamlConfiguration config = new YamlConfiguration();
    final World world = mock(World.class, "world");
    /** Attribute types can't be built without a server registry; each body answers any type with its one instance. */
    final List<Attribute> attributes = Arrays.asList((Attribute) null);
    final NamespacedKey buffKey = new NamespacedKey("sneaky", "buff");
    /** Gameplay files written, by file name, in order. */
    final List<PendingWrite> writes = new ArrayList<>();
    /** What happened, in order: "teleport:Driver", "skin:Driver", "save:main.yml", ... */
    final List<String> log = new ArrayList<>();
    final List<Event> fired = new ArrayList<>();
    boolean deferTasks;
    final List<Runnable> deferred = new ArrayList<>();
    /** Tasks scheduled for a later tick; run with {@link #nextTick}. */
    final List<Runnable> later = new ArrayList<>();
    /** Bodies whose skin event a listener cancels. */
    final Set<Player> skinVetoed = new java.util.HashSet<>();
    /** Runs inside the given event, like a listener; null for none. */
    Consumer<Event> listener;
    /**
     * Runs inside each call that fires an event on a body, like its listener: "close" (InventoryCloseEvent),
     * "skinEvent" (CharacterSkinAppliedEvent), "teleport", "clear" and "effect" (potion effects), "air"
     * (EntityAirChangeEvent). Null for none.
     */
    BiConsumer<Body, String> callOut;
    /** Items dropped on the ground, in order. */
    final List<ItemStack> dropped = new ArrayList<>();
    PluginManager events;
    Body driver, supporter, third;
    DatabaseBackend backend;
    MockedStatic<SneakyCharacterManager> plugins;
    MockedStatic<Bukkit> bukkit;
    MockedStatic<SkinUtil> skins;
    MockedStatic<SkinCache> cache;
    MockedStatic<InventoryUtility> items;
    MockedStatic<LiveState> attributeTypes;
    MockedStatic<PlaceholderUtil> placeholders;

    record PendingWrite(Path target, String content) {}

    /** The puppet test's fake SneakyDB, with a spare character and pointer writes that can fail. */
    static class Store extends PuppetApiTest.Database {
        boolean failPointers;
        @Override public CompletableFuture<Integer> execute(String sql, List<?> args) {
            if (failPointers && sql.contains("SET last_played_character = ?")) {
                statements.add(sql); bindings.add(args);
                return CompletableFuture.failedFuture(new IllegalStateException("database down"));
            }
            return super.execute(sql, args);
        }
    }

    /** A body: a Player mock whose getters return what its setters stored. */
    final class Body {
        final Player player;
        final String name;
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final InventoryView view = mock(InventoryView.class);
        final CraftingInventory grid = mock(CraftingInventory.class);
        final Inventory chest = mock(Inventory.class);
        final AttributeInstance scaleInstance = mock(AttributeInstance.class);
        Location location;
        Vector velocity = new Vector();
        float fallDistance;
        double health = 20, absorption, maxHealth = 20;
        int food = 20, fire, freeze, air = 300, slot;
        float saturation = 5, exhaustion;
        List<PotionEffect> effects = new ArrayList<>();
        ItemStack[] contents = new ItemStack[41];
        ItemStack cursor;
        ItemStack[] matrix = new ItemStack[4];
        double scaleBase = 1;
        List<AttributeModifier> modifiers = new ArrayList<>();
        ProfileProperty skin;
        boolean chestOpen;
        Entity vehicle;
        boolean dead, sleeping, refuseTeleport;

        Body(UUID id, String name, double x) {
            this.name = name;
            this.location = new Location(world, x, 0, 0);
            player = mock(Player.class, name);
            when(player.getUniqueId()).thenReturn(id);
            when(player.getName()).thenReturn(name);
            when(player.isOnline()).thenAnswer(call -> Bukkit.getPlayer(id) == player);
            when(player.hasPermission("sneakycharacters.admin.bypass.characterlocks")).thenReturn(true);
            when(player.getLocation()).thenAnswer(call -> location.clone());
            when(player.getWorld()).thenAnswer(call -> location.getWorld());
            when(player.isDead()).thenAnswer(call -> dead);
            when(player.isSleeping()).thenAnswer(call -> sleeping);
            when(player.teleport(any(Location.class))).thenAnswer(call -> teleport(call.getArgument(0)));
            when(player.getVelocity()).thenAnswer(call -> velocity.clone());
            doAnswer(call -> { velocity = ((Vector) call.getArgument(0)).clone(); log("velocity"); return null; }).when(player).setVelocity(any());
            when(player.getFallDistance()).thenAnswer(call -> fallDistance);
            doAnswer(call -> { fallDistance = call.getArgument(0); return null; }).when(player).setFallDistance(anyFloat());
            when(player.getHealth()).thenAnswer(call -> health);
            doAnswer(call -> {
                double to = call.getArgument(0);
                if (to > maxHealth) throw new IllegalArgumentException("above maximum"); // as Paper
                health = to;
                log("health");
                return null;
            }).when(player).setHealth(anyDouble());
            when(player.getAbsorptionAmount()).thenAnswer(call -> absorption);
            doAnswer(call -> { absorption = call.getArgument(0); return null; }).when(player).setAbsorptionAmount(anyDouble());
            when(player.getFoodLevel()).thenAnswer(call -> food);
            doAnswer(call -> { food = call.getArgument(0); return null; }).when(player).setFoodLevel(anyInt());
            when(player.getSaturation()).thenAnswer(call -> saturation);
            doAnswer(call -> { saturation = call.getArgument(0); return null; }).when(player).setSaturation(anyFloat());
            when(player.getExhaustion()).thenAnswer(call -> exhaustion);
            doAnswer(call -> { exhaustion = call.getArgument(0); return null; }).when(player).setExhaustion(anyFloat());
            when(player.getActivePotionEffects()).thenAnswer(call -> new ArrayList<>(effects));
            when(player.clearActivePotionEffects()).thenAnswer(call -> { effects.clear(); log("effects"); callOut("clear"); return true; });
            when(player.addPotionEffect(any(PotionEffect.class))).thenAnswer(call -> {
                effects.add(call.getArgument(0));
                callOut("effect");
                return true;
            });
            when(player.getFireTicks()).thenAnswer(call -> fire);
            doAnswer(call -> { fire = call.getArgument(0); return null; }).when(player).setFireTicks(anyInt());
            when(player.getFreezeTicks()).thenAnswer(call -> freeze);
            doAnswer(call -> { freeze = call.getArgument(0); return null; }).when(player).setFreezeTicks(anyInt());
            when(player.getRemainingAir()).thenAnswer(call -> air);
            doAnswer(call -> { air = call.getArgument(0); callOut("air"); return null; }).when(player).setRemainingAir(anyInt());
            when(player.getInventory()).thenReturn(inventory);
            when(inventory.getSize()).thenReturn(41);
            when(inventory.getContents()).thenAnswer(call -> contents.clone());
            doAnswer(call -> { contents = ((ItemStack[]) call.getArgument(0)).clone(); log("inventory"); return null; }).when(inventory).setContents(any());
            doAnswer(call -> { contents = new ItemStack[41]; return null; }).when(inventory).clear();
            when(inventory.getHeldItemSlot()).thenAnswer(call -> slot);
            doAnswer(call -> { slot = call.getArgument(0); return null; }).when(inventory).setHeldItemSlot(anyInt());
            when(player.getItemOnCursor()).thenAnswer(call -> cursor);
            doAnswer(call -> { cursor = call.getArgument(0); return null; }).when(player).setItemOnCursor(any());
            when(player.getOpenInventory()).thenReturn(view);
            when(view.getTopInventory()).thenAnswer(call -> chestOpen ? chest : grid);
            when(grid.getMatrix()).thenAnswer(call -> matrix.clone());
            doAnswer(call -> { closeInventory(); return null; }).when(player).closeInventory();
            when(player.getAttribute(any())).thenReturn(scaleInstance);
            when(scaleInstance.getBaseValue()).thenAnswer(call -> scaleBase);
            doAnswer(call -> { scaleBase = call.getArgument(0); return null; }).when(scaleInstance).setBaseValue(anyDouble());
            when(scaleInstance.getModifiers()).thenAnswer(call -> new ArrayList<>(modifiers));
            doAnswer(call -> { modifiers.add(call.getArgument(0)); return null; }).when(scaleInstance).addModifier(any());
            doAnswer(call -> { modifiers.remove((AttributeModifier) call.getArgument(0)); return null; }).when(scaleInstance).removeModifier(any(AttributeModifier.class));
            when(player.getVehicle()).thenAnswer(call -> vehicle);
            when(player.isInsideVehicle()).thenAnswer(call -> vehicle != null);
            when(player.leaveVehicle()).thenAnswer(call -> { boolean riding = vehicle != null; vehicle = null; return riding; });
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
        }

        /** A teleport fires SCM's real teleport listener, then any test listener, like Paper's event. */
        private boolean teleport(Location to) {
            log("teleport");
            PlayerTeleportEvent event = new PlayerTeleportEvent(player, location.clone(), to.clone());
            new TeleportEvents().onGamemodeChange(event);
            if (listener != null) listener.accept(event);
            callOut("teleport");
            if (refuseTeleport) return false;
            location = event.getTo().clone(); // a listener may have redirected it
            velocity = new Vector(); // Paper's teleport zeroes velocity and fall distance
            fallDistance = 0;
            return true;
        }

        void log(String what) {
            SwapTest.this.log.add(what + ":" + name);
        }

        void callOut(String what) {
            if (SwapTest.this.callOut != null) SwapTest.this.callOut.accept(this, what);
        }

        /** Into the first free main-inventory slot; false if it is full. */
        boolean store(ItemStack item) {
            for (int i = 0; i < 36; i++) {
                if (contents[i] == null) { contents[i] = item; return true; }
            }
            return false;
        }

        /**
         * As Paper's closeInventory: InventoryCloseEvent first (a "close" call-out), then the menu is removed, which
         * puts the cursor item and the 2x2 grid into the inventory, dropping what doesn't fit, or all of it once the
         * player has disconnected.
         */
        private void closeInventory() {
            log("close");
            callOut("close");
            chestOpen = false;
            boolean connected = Bukkit.getPlayer(player.getUniqueId()) == player;
            List<ItemStack> back = new ArrayList<>();
            if (cursor != null) back.add(cursor);
            for (ItemStack item : matrix) if (item != null) back.add(item);
            cursor = null;
            matrix = new ItemStack[4];
            for (ItemStack item : back) if (!connected || !store(item)) dropped.add(item);
        }

        long count(ItemStack item) {
            return java.util.stream.Stream.concat(Arrays.stream(contents), Arrays.stream(matrix)).filter(item::equals).count()
                    + (item.equals(cursor) ? 1 : 0);
        }

        void offline() {
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(null);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        when(world.key()).thenReturn(NamespacedKey.minecraft("world")); // a save writes the world's key
        when(world.dropItemNaturally(any(Location.class), any(ItemStack.class))).thenAnswer(call -> {
            dropped.add(call.getArgument(1));
            return null;
        });
        api.ready.complete(null);
        api.characters.put(driverId.toString(), List.of(new String[]{"main", "Main Hero"}, new String[]{"second", "Second Self"},
                new String[]{"spare", "Spare Self"}));
        api.characters.put(supporterId.toString(), List.<String[]>of(new String[]{"own", "Supporter Self"}));
        api.characters.put(thirdId.toString(), List.<String[]>of(new String[]{"third", "Third Self"}));
        api.lastPlayed.put(driverId.toString(), "main");
        api.lastPlayed.put(supporterId.toString(), "own");
        api.lastPlayed.put(thirdId.toString(), "third");
        plugins = mockStatic(SneakyCharacterManager.class);
        bukkit = mockStatic(Bukkit.class);
        skins = mockStatic(SkinUtil.class);
        cache = mockStatic(SkinCache.class, CALLS_REAL_METHODS);
        items = mockStatic(InventoryUtility.class);
        // Only the attribute types are replaced; capturing and applying state run for real.
        attributeTypes = mockStatic(LiveState.class, call -> switch (call.getMethod().getName()) {
            case "attributeTypes" -> attributes;
            case "maxHealth" -> body(call.getArgument(0)).maxHealth;
            default -> call.callRealMethod();
        });
        placeholders = mockStatic(PlaceholderUtil.class);
        // A save records which items it saw, so a gameplay write shows whose inventory it holds.
        items.when(() -> InventoryUtility.inventoryToPaperYaml(any(Inventory.class)))
                .thenAnswer(call -> Arrays.toString(((Inventory) call.getArgument(0)).getContents()));
        plugins.when(SneakyCharacterManager::getInstance).thenReturn(plugin);
        plugins.when(SneakyCharacterManager::getCharacterDataFolder).thenReturn(data.toFile());
        skins.when(() -> SkinUtil.getTextureUrl(any())).thenReturn("http://textures.minecraft.net/texture/stored");
        skins.when(() -> SkinUtil.wornTextures(any())).thenAnswer(call -> body(call.getArgument(0)).skin);
        skins.when(() -> SkinUtil.skinAllowed(any(), any())).thenAnswer(call -> {
            body(call.getArgument(0)).log("skinEvent");
            body(call.getArgument(0)).callOut("skinEvent");
            return !skinVetoed.contains(call.getArgument(0));
        });
        skins.when(() -> SkinUtil.putSkin(any(), any())).thenAnswer(call -> {
            Body body = body(call.getArgument(0));
            body.skin = call.getArgument(1);
            body.log("skin");
            return null;
        });
        config.set("manageInventories", true);
        config.set("manageAttributes", false); // a load that applies attributes needs the server's registry
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getStorageMode()).thenReturn(StorageMode.SNEAKYDB);
        when(plugin.writeFile(any(), anyString())).thenAnswer(call -> {
            Path target = call.getArgument(0);
            writes.add(new PendingWrite(target, call.getArgument(1)));
            log.add("save:" + target.getFileName());
            return CompletableFuture.completedFuture(null);
        });
        plugin.skinStateManager = new SkinStateManager();
        plugin.skinQueue = mock(SkinQueue.class);
        plugin.nametagManager = mock(NametagManager.class);
        plugin.selectionMenu = mock(CharacterSelectionMenu.class);
        plugin.titleRegistry = mock(TitleRegistry.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            if (deferTasks) deferred.add(call.getArgument(1));
            else ((Runnable) call.getArgument(1)).run();
            return null;
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
            later.add(call.getArgument(1));
            return null;
        });
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> List.of());
        events = mock(PluginManager.class);
        doAnswer(call -> {
            Event event = call.getArgument(0);
            fired.add(event);
            if (listener != null) listener.accept(event);
            return null;
        }).when(events).callEvent(any());
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(events);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getPluginManager).thenReturn(events);
        driver = new Body(driverId, "Driver", 1);
        supporter = new Body(supporterId, "Supporter", 2);
        third = new Body(thirdId, "Staff", 3);
        gameplayFile(main, 10);
        gameplayFile(second, 20);
        gameplayFile(spare, 25);
        gameplayFile(supportersOwn, 30);
        gameplayFile(thirdsOwn, 40);
        backend = new DatabaseBackend(plugin, new SqlStore(api, 5));
        when(plugin.getCharacterBackend()).thenReturn(backend);
        driver.skin = new ProfileProperty("textures", "tex-main", "sig-main");
        supporter.skin = new ProfileProperty("textures", "tex-own", "sig-own");
        join(driver);
        join(supporter);
        assertEquals(PuppetResult.PUPPETED, Puppets.puppet(supporter.player, second).join());
        supporter.skin = new ProfileProperty("textures", "tex-second-uniform", "sig-second-uniform");
        assertEquals(main, Character.get(driver.player).getKey());
        assertEquals(second, Character.get(supporter.player).getKey());
        api.forget(); writes.clear(); log.clear(); fired.clear();
        clearInvocations(plugin.nametagManager, driver.player, supporter.player);
    }

    @AfterEach
    void tearDown() {
        try {
            if (backend != null) backend.close();
            for (Body body : new Body[]{driver, supporter, third}) if (body != null) Character.remove(body.player, Reason.QUIT);
        } finally {
            for (MockedStatic<?> mock : List.of(placeholders, attributeTypes, items, cache, skins, bukkit, plugins)) mock.close();
        }
    }

    // W1 Who wears what

    @Test
    void eachBodyWearsTheOthersCharacterAndThePuppetMovesWithoutLeaveOrLoadEvents() {
        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        assertEquals(second, Character.get(driver.player).getKey());
        assertEquals(main, Character.get(supporter.player).getKey());
        assertSame(driver.player, CharacterHolders.holderOf(second));
        assertSame(supporter.player, CharacterHolders.holderOf(main));
        assertSame(driver.player, Character.get(driver.player).getPlayer(), "the character's wearer moved too");
        assertSame(supporter.player, Character.get(supporter.player).getPlayer());
        assertNull(Puppets.borrowedBy(driver.player), "the Driver wears their own character now");
        assertEquals(main, Puppets.borrowedBy(supporter.player), "the Supporter puppets the Driver's former character");
        assertEquals(List.of(), fired.stream().filter(e -> e instanceof PuppetLeaveEvent || e instanceof LoadCharacterEvent).toList());
    }

    @Test
    void theSwapIsSymmetric() {
        assertEquals(SwapResult.SWAPPED, Puppets.swap(supporter.player, driver.player));

        assertEquals(second, Character.get(driver.player).getKey());
        assertEquals(main, Character.get(supporter.player).getKey());
        assertEquals(List.of(driverId + "/second"), api.pointerWrites());
    }

    // W2 State

    @Test
    void eachBodyTakesTheOthersSpotAndLiveState() {
        ItemStack sword = item("sword"), bread = item("bread"), stick = item("stick"), shield = item("shield"),
                rope = item("rope"), torch = item("torch");
        PotionEffect speed = mock(PotionEffect.class, "speed"), glow = mock(PotionEffect.class, "glow");
        Location driverSpot = new Location(world, 10.5, 65, 3.25, 90f, 10f);
        Location supporterSpot = new Location(world, -4, 70, 8.5, -45f, -20f);
        driver.location = driverSpot.clone(); supporter.location = supporterSpot.clone();
        driver.velocity = new Vector(0.1, 0.2, 0.3); supporter.velocity = new Vector(-1, 0, 0);
        driver.fallDistance = 2.5f; supporter.fallDistance = 0.5f;
        driver.health = 7; supporter.health = 15;
        driver.absorption = 2; supporter.absorption = 0;
        driver.food = 11; supporter.food = 18;
        driver.saturation = 3; supporter.saturation = 1;
        driver.exhaustion = 1.5f; supporter.exhaustion = 0.25f;
        driver.effects = new ArrayList<>(List.of(speed)); supporter.effects = new ArrayList<>(List.of(glow));
        driver.fire = 40; supporter.fire = 0;
        driver.freeze = 20; supporter.freeze = 5;
        driver.air = 100; supporter.air = 300;
        driver.contents[0] = sword; supporter.contents[0] = bread;
        driver.cursor = stick; supporter.cursor = shield;
        driver.matrix[0] = rope; supporter.matrix[3] = torch;
        driver.slot = 4; supporter.slot = 7;
        driver.scaleBase = 0.8; supporter.scaleBase = 1.1;
        ProfileProperty mainSkin = driver.skin, secondSkin = supporter.skin;

        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        // The Supporter now is what the Driver was, where the Driver stood ...
        assertEquals(driverSpot, supporter.location);
        assertEquals(new Vector(0.1, 0.2, 0.3), supporter.velocity);
        assertEquals(2.5f, supporter.fallDistance);
        assertEquals(7, supporter.health);
        assertEquals(2, supporter.absorption);
        assertEquals(11, supporter.food);
        assertEquals(3, supporter.saturation);
        assertEquals(1.5f, supporter.exhaustion);
        assertEquals(List.of(speed), supporter.effects);
        assertEquals(40, supporter.fire);
        assertEquals(20, supporter.freeze);
        assertEquals(100, supporter.air);
        assertSame(sword, supporter.contents[0]);
        assertEquals(1, supporter.count(stick), "the cursor item, through main's inventory");
        assertEquals(1, supporter.count(rope), "and the grid");
        assertNull(supporter.cursor);
        assertArrayEquals(new ItemStack[4], supporter.matrix);
        assertEquals(4, supporter.slot);
        assertEquals(0.8, supporter.scaleBase);
        assertSame(mainSkin, supporter.skin);
        // ... and the other way round.
        assertEquals(supporterSpot, driver.location);
        assertEquals(new Vector(-1, 0, 0), driver.velocity);
        assertEquals(0.5f, driver.fallDistance);
        assertEquals(15, driver.health);
        assertEquals(18, driver.food);
        assertEquals(List.of(glow), driver.effects);
        assertEquals(5, driver.freeze);
        assertSame(bread, driver.contents[0]);
        assertEquals(1, driver.count(shield));
        assertEquals(1, driver.count(torch));
        assertNull(driver.cursor);
        assertEquals(7, driver.slot);
        assertEquals(1.1, driver.scaleBase);
        assertSame(secondSkin, driver.skin, "the live property moves, uniform and all");
        // Plugin attribute modifiers (when SCM manages attributes) are covered by LiveStateTest.
    }

    @Test
    void bothInventoriesAreClosedFirst() {
        supporter.chestOpen = true;

        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        assertTrue(log.contains("close:Supporter"), log.toString());
        assertTrue(log.indexOf("close:Supporter") < log.indexOf("save:main.yml"));
        assertTrue(log.contains("close:Driver") && log.indexOf("close:Driver") < log.indexOf("save:main.yml"),
                "the player's own inventory view is closed too, so its cursor and grid go into the inventory");
        assertFalse(supporter.chestOpen);
    }

    // W3 Order, nameplate, skin history, poses

    @Test
    void teleportFirstSkinLastAndSavesBeforeAnythingMoves() {
        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        int firstTeleport = log.indexOf("teleport:Driver");
        assertTrue(log.indexOf("save:main.yml") < firstTeleport && log.indexOf("save:second.yml") < firstTeleport, log.toString());
        for (String body : List.of("Driver", "Supporter")) {
            int teleport = log.indexOf("teleport:" + body), skin = log.indexOf("skin:" + body);
            for (String step : List.of("effects", "health", "inventory", "velocity")) {
                int at = log.indexOf(step + ":" + body);
                assertTrue(teleport < at && at < skin, step + " between teleport and skin: " + log);
            }
        }
        assertEquals(List.of("teleport:Driver", "teleport:Supporter"), log.stream().filter(s -> s.startsWith("teleport")).toList(),
                "no +1 Y hop or second teleport");
    }

    @Test
    void theNameplateIsReTextedInPlaceNotRebuilt() {
        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        verify(plugin.nametagManager).showWornCharacter(driver.player);
        verify(plugin.nametagManager).showWornCharacter(supporter.player);
        verify(plugin.nametagManager, never()).rebuild(any());
        verify(plugin.nametagManager, never()).unnicknamePlayer(any());
        verify(plugin.nametagManager, never()).nicknamePlayer(any(), any());

        // Outside a swap the same teleport listener still rebuilds.
        new TeleportEvents().onGamemodeChange(new PlayerTeleportEvent(driver.player, driver.location, driver.location));
        verify(plugin.nametagManager).rebuild(driver.player);
    }

    @Test
    void theSkinHistoryMovesWithEachCharacter() {
        SkinStateManager states = plugin.skinStateManager;
        assertNotNull(states.latestForCharacter(driverId, main)); // recorded when each loaded
        assertNotNull(states.latestForCharacter(supporterId, second));

        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        assertNull(states.latestForCharacter(driverId, main));
        assertNull(states.latestForCharacter(supporterId, second));
        assertEquals(second, states.current(driverId).characterKey());
        assertEquals(main, states.current(supporterId).characterKey());
    }

    @Test
    void posesGoBackOnAfterTheSkinAndRidersAreReSeated() {
        Plugin poses = mock(Plugin.class);
        YamlConfiguration posesConfig = new YamlConfiguration();
        posesConfig.set("sit.y-offset", 0.25);
        when(poses.isEnabled()).thenReturn(true);
        when(poses.getConfig()).thenReturn(posesConfig);
        when(events.getPlugin("SneakyPoses")).thenReturn(poses);
        placeholders.when(PlaceholderUtil::isAvailable).thenReturn(true);
        placeholders.when(() -> PlaceholderUtil.setPlaceholders(any(Player.class), anyString())).thenAnswer(call -> {
            String text = call.getArgument(1);
            boolean isDriver = call.getArgument(0) == driver.player;
            return String.valueOf(isDriver && text.equals("%sneakyposes_sitting%")
                    || !isDriver && text.equals("%sneakyposes_crawling%"));
        });
        when(world.getName()).thenReturn("world");
        Entity seat = mock(Entity.class, "seat");
        when(seat.getScoreboardTags()).thenReturn(Set.of("SneakyPosesSeat"));
        when(seat.getLocation()).thenReturn(new Location(world, 10.5, 64.75, 3.25, 90f, 0f));
        driver.vehicle = seat;
        bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
        bukkit.when(() -> Bukkit.dispatchCommand(any(), anyString())).thenAnswer(call -> { log.add("command:" + call.getArgument(1)); return true; });

        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        int skins = Math.max(log.indexOf("skin:Driver"), log.indexOf("skin:Supporter"));
        int sit = log.indexOf("command:sit true Supporter world,10.5,64.5,3.25,90.0");
        int crawl = log.indexOf("command:crawl true Driver");
        assertTrue(skins < sit && skins < crawl, log.toString());
        verify(seat, never()).addPassenger(any()); // a pose seat is put back as a pose, not ridden
    }

    @Test
    void aBodyOnAVehicleHandsItsSeatToTheOther() {
        Entity horse = mock(Entity.class, "horse");
        when(horse.isValid()).thenReturn(true);
        supporter.vehicle = horse;

        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        verify(horse).addPassenger(driver.player);
    }

    // W4 Body-level state when SCM doesn't manage it

    @Test
    void unmanagedInventoriesAndModifiersStayWithTheBody() {
        config.set("manageInventories", false);
        config.set("manageAttributes", false);
        ItemStack sword = item("sword");
        AttributeModifier buff = new AttributeModifier(buffKey, 0.2, AttributeModifier.Operation.ADD_NUMBER);
        driver.contents[0] = sword; driver.cursor = item("stick"); driver.matrix[0] = item("rope");
        driver.modifiers.add(buff); driver.scaleBase = 0.8;

        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        assertSame(sword, driver.contents[0]);
        assertEquals(2, Arrays.stream(driver.contents).filter(Objects::nonNull).count() - 1,
                "the cursor and grid went into its own inventory, which stays");
        assertNull(supporter.contents[0]);
        assertEquals(List.of(buff), driver.modifiers);
        assertEquals(0.8, supporter.scaleBase, "base values always move: a swap must not change how big a character is");
    }

    // I1 No save in the middle; I4 one save per character

    @Test
    void eachCharacterIsSavedOnceWhereItStoodAndListenersCantSaveMidSwap() {
        driver.contents[0] = item("sword");
        supporter.contents[0] = item("bread");
        listener = event -> {
            if (event instanceof PlayerTeleportEvent) {
                Character.get(driver.player).save();
                Character.get(supporter.player).save();
                Character.saveAll();
            }
        };

        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        assertEquals(List.of(file(main), file(second)), writes.stream().map(PendingWrite::target).toList());
        assertEquals(new Location(null, 10, 65, 0), location(writes.get(0)), "main where the Driver stood");
        assertTrue(writes.get(0).content().contains("sword"));
        assertEquals(new Location(null, 20, 65, 0), location(writes.get(1)), "second where the Supporter stood");
        assertTrue(writes.get(1).content().contains("bread"));

        // Afterwards saves work again, and land on the owner's files for the new wearers.
        listener = null;
        Character.saveAll();
        assertEquals(4, writes.size());
        assertEquals(Set.of(file(main), file(second)), Set.of(writes.get(2).target(), writes.get(3).target()));
    }

    // I2 No load in the middle

    @Test
    void noLoadCanRunInTheMiddle() {
        join(third);
        List<Boolean> loads = new ArrayList<>();
        listener = event -> {
            if (!(event instanceof PlayerTeleportEvent) || !loads.isEmpty()) return;
            loads.add(character(spare, driver.player).load(LoadReason.OWN_SELECT)); // onto a swapping body
            loads.add(character(main, third.player).load(LoadReason.OTHER)); // a swapping character onto anyone
            loads.add(character(second, third.player).load(LoadReason.OTHER));
        };

        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        assertEquals(List.of(false, false, false), loads);
        assertEquals(thirdsOwn, Character.get(third.player).getKey());
        assertEquals(second, Character.get(driver.player).getKey());
    }

    @Test
    void theOwnerOfASwappingCharacterCantTakeItOverMidSwap() {
        // Not a dance: the Supporter (puppeting the Driver's second) swaps with a third body, so the owner isn't swapping.
        join(third);
        api.forget();
        List<Boolean> takeovers = new ArrayList<>();
        listener = event -> {
            if (event instanceof PlayerTeleportEvent && takeovers.isEmpty())
                takeovers.add(character(second, driver.player).load(LoadReason.OWN_JOIN));
        };

        assertEquals(SwapResult.SWAPPED, Puppets.swap(supporter.player, third.player));

        assertEquals(List.of(false), takeovers);
        assertEquals(second, Character.get(third.player).getKey());
        assertEquals(thirdsOwn, Character.get(supporter.player).getKey());
        assertEquals(main, Character.get(driver.player).getKey());
        assertEquals(List.of(), api.pointerWrites(), "neither body wears its own character now");
    }

    @Test
    void aSwapFromInsideASwapOrALoadIsBusy() {
        List<SwapResult> nested = new ArrayList<>();
        listener = event -> {
            if (event instanceof PlayerTeleportEvent && nested.isEmpty()) nested.add(Puppets.swap(driver.player, supporter.player));
        };
        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));
        assertEquals(List.of(SwapResult.BUSY), nested);

        nested.clear();
        listener = event -> {
            if (event instanceof LoadCharacterEvent) nested.add(Puppets.swap(driver.player, supporter.player));
        };
        backend.submit(driver.player, new CharacterRequest.Select(driverId.toString(), "spare"));
        assertEquals(List.of(SwapResult.BUSY), nested);
        assertEquals(spare, Character.get(driver.player).getKey());
    }

    // I3 No quit in the middle

    @Test
    void aBodyKickedMidSwapSavesNothingMoreAndTheSwapIsInterrupted() {
        // A listener of the Driver's skin event kicks the Driver, after the exchange.
        skins.when(() -> SkinUtil.putSkin(any(), any())).thenAnswer(call -> {
            Body body = body(call.getArgument(0));
            body.skin = call.getArgument(1);
            body.log("skin");
            if (body == driver) kick(driver);
            return null;
        });

        assertEquals(SwapResult.INTERRUPTED, Puppets.swap(driver.player, supporter.player));

        assertEquals(List.of(file(main), file(second)), writes.stream().map(PendingWrite::target).toList(),
                "the quit's save was skipped; the swap's own saves stand");
        assertNull(Character.get(driver.player));
        assertNull(CharacterHolders.holderOf(second));
        assertEquals(main, Character.get(supporter.player).getKey());
        assertEquals(List.of(), api.pointerWrites(), "an offline Driver's pointer isn't moved");
    }

    @Test
    void aReleaseFromInsideASwapWaitsUntilTheSwapIsDone() {
        // After the exchange the Supporter puppets main; a listener of the Driver's effects releases the Supporter.
        ItemStack sword = item("sword"), stick = item("stick");
        driver.contents[0] = sword; driver.cursor = stick;
        ItemStack bread = item("bread");
        supporter.contents[0] = bread;
        supporter.effects = new ArrayList<>(List.of(mock(PotionEffect.class, "glow"))); // the Driver gets these: their event releases
        deferTasks = true; // the Supporter's return load arrives after the swap, as storage results do
        List<ReleaseResult> released = new ArrayList<>();
        List<CharacterKey> borrowedThen = new ArrayList<>();
        callOut = (body, what) -> {
            if (body != driver || !what.equals("effect") || !released.isEmpty()) return;
            released.add(Puppets.release(supporter.player));
            borrowedThen.add(Puppets.borrowedBy(supporter.player));
        };

        assertEquals(SwapResult.INTERRUPTED, Puppets.swap(driver.player, supporter.player));

        assertEquals(List.of(ReleaseResult.RELEASED), released);
        assertEquals(List.of(main), borrowedThen, "still worn until the swap is done");
        assertEquals(List.of(file(main), file(second), file(main)), writes.stream().map(PendingWrite::target).toList(),
                "the release saved main as usual, once the swap was done");
        assertEquals(new Location(null, 10, 65, 0), location(writes.get(2)), "where the Supporter now stood");
        assertTrue(writes.get(2).content().contains("sword"), "with what the swap moved");
        assertNull(supporter.contents[0], "then cleared, as a release does");
        assertTrue(writes.get(0).content().contains("stick") && writes.get(2).content().contains("stick"),
                "the cursor item went into main's inventory before the swap's save");
        assertEquals(0, supporter.count(stick) + driver.count(stick));
        assertNull(Character.get(supporter.player));
        List<PuppetLeaveEvent> leaves = fired.stream().filter(e -> e instanceof PuppetLeaveEvent).map(e -> (PuppetLeaveEvent) e).toList();
        assertEquals(1, leaves.size());
        assertEquals(main, leaves.get(0).getCharacterKey());
        assertEquals(Reason.RELEASED, leaves.get(0).getReason());
        // The Driver's half went through.
        assertEquals(second, Character.get(driver.player).getKey());
        assertSame(bread, driver.contents[0]);

        runDeferred();
        assertEquals(supportersOwn, Character.get(supporter.player).getKey(), "the release's return load still arrives");
        assertNull(CharacterHolders.holderOf(main));
    }

    @Test
    void aBodyLockedOutDuringItsOwnEffectsGetsNothingMoreOfItsNewCharacter() {
        // The Supporter goes first and takes main; a listener of its own effects locks it out (no save, nothing waits).
        ItemStack sword = item("sword"), stick = item("stick"), rope = item("rope");
        driver.contents[0] = sword; driver.cursor = stick; driver.matrix[0] = rope; driver.health = 7; driver.scaleBase = 0.8;
        driver.effects = new ArrayList<>(List.of(mock(PotionEffect.class, "speed"))); // the Supporter gets these
        callOut = (body, what) -> {
            if (body == supporter && what.equals("effect") && Character.get(supporter.player) != null) lockOut(supporter);
        };

        assertEquals(SwapResult.INTERRUPTED, Puppets.swap(supporter.player, driver.player));

        assertNull(Character.get(supporter.player));
        assertEquals(1, supporter.count(stick), "the items had moved before");
        assertEquals(1, supporter.count(rope));
        assertEquals(0, driver.count(stick) + driver.count(rope), "and the Driver keeps no copy");
        assertNotEquals(7, supporter.health, "nothing after the lock-out");
        assertNotEquals(0.8, supporter.scaleBase);
        assertEquals(2, writes.size());
        assertEquals(second, Character.get(driver.player).getKey());
    }

    @Test
    void aBodyReleasedMidSwapLeavesNoCopyOfItsCursorOrGridItems() {
        // The Supporter's cursor and grid hold diamonds; a listener of the Driver's effects releases the Supporter.
        ItemStack diamonds = item("diamonds"), moreDiamonds = item("more diamonds"), stick = item("stick"), rope = item("rope");
        supporter.cursor = diamonds; supporter.matrix[0] = moreDiamonds;
        driver.cursor = stick; driver.matrix[2] = rope;
        deferTasks = true;
        callOut = (body, what) -> {
            if (body == driver && what.equals("effect") && Puppets.borrowedBy(supporter.player) != null) Puppets.release(supporter.player);
        };
        driver.effects = new ArrayList<>(List.of(mock(PotionEffect.class, "speed")));
        supporter.effects = new ArrayList<>(List.of(mock(PotionEffect.class, "glow")));

        assertEquals(SwapResult.INTERRUPTED, Puppets.swap(driver.player, supporter.player));
        runDeferred();

        assertEquals(1, driver.count(diamonds), "second's items went to its new wearer");
        assertEquals(1, driver.count(moreDiamonds));
        assertEquals(0, supporter.count(diamonds) + supporter.count(moreDiamonds), "and stayed nowhere else");
        assertTrue(writes.get(2).content().contains("stick") && writes.get(2).content().contains("rope"),
                "main's went into the release's save");
        assertEquals(0, supporter.count(stick) + supporter.count(rope) + driver.count(stick) + driver.count(rope));
        assertEquals(List.of(), dropped);
    }

    @Test
    void theCursorAndGridGoIntoTheInventoryBeforeTheSave() {
        ItemStack stick = item("stick"), rope = item("rope");
        driver.cursor = stick; driver.matrix[1] = rope;

        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        assertTrue(writes.get(0).content().contains("stick") && writes.get(0).content().contains("rope"), "main's save holds them");
        assertEquals(1, supporter.count(stick));
        assertEquals(1, supporter.count(rope));
        assertEquals(0, driver.count(stick) + driver.count(rope));
        assertTrue(log.indexOf("close:Driver") < log.indexOf("save:main.yml"), log.toString());
    }

    @Test
    void aFullInventoryDropsTheCursorAndGridAtTheBodyAsVanillaDoes() {
        ItemStack stick = item("stick"), rope = item("rope");
        for (int i = 0; i < 36; i++) driver.contents[i] = item("filler " + i);
        driver.cursor = stick; driver.matrix[0] = rope;

        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        assertEquals(List.of(stick, rope), dropped);
        assertFalse(writes.get(0).content().contains("stick"));
        assertEquals(0, driver.count(stick) + supporter.count(stick) + driver.count(rope) + supporter.count(rope));
    }

    @ParameterizedTest(name = "kicking the {1} at {0}")
    @CsvSource({"close, Driver", "close, Supporter", "skinEvent, Driver", "skinEvent, Supporter", "teleport, Driver",
            "teleport, Supporter", "effect, Driver", "effect, Supporter", "air, Driver", "air, Supporter"})
    void aKickAtAnyStepLosesOrCopiesNoItem(String step, String who) {
        ItemStack sword = item("sword"), stick = item("stick"), rope = item("rope"),
                bread = item("bread"), shield = item("shield"), torch = item("torch");
        driver.contents[0] = sword; driver.cursor = stick; driver.matrix[0] = rope;
        supporter.contents[0] = bread; supporter.cursor = shield; supporter.matrix[3] = torch;
        driver.effects = new ArrayList<>(List.of(mock(PotionEffect.class, "speed")));
        supporter.effects = new ArrayList<>(List.of(mock(PotionEffect.class, "glow")));
        Body kicked = who.equals("Driver") ? driver : supporter;
        List<String> kicks = new ArrayList<>();
        callOut = (body, what) -> {
            if (body != kicked || !what.equals(step) || !kicks.isEmpty()) return;
            kicks.add(step);
            kick(kicked);
        };

        SwapResult result = Puppets.swap(driver.player, supporter.player);

        assertEquals(List.of(step), kicks, "the kick happened");
        assertTrue(result == SwapResult.REFUSED || result == SwapResult.INTERRUPTED, result.toString());
        for (ItemStack item : List.of(sword, stick, rope)) assertConserved(item, main);
        for (ItemStack item : List.of(bread, shield, torch)) assertConserved(item, second);
    }

    /**
     * {@code item}, of {@code character}, exists exactly once: on the online body wearing it, or, if none does, in
     * the character's latest save; or else on the ground. No other online body holds it.
     */
    private void assertConserved(ItemStack item, CharacterKey character) {
        Body wearer = null;
        long live = 0;
        for (Body body : new Body[]{driver, supporter}) {
            if (Bukkit.getPlayer(body.player.getUniqueId()) != body.player) continue;
            live += body.count(item);
            if (Character.isWearing(body.player, character)) wearer = body;
        }
        long onGround = dropped.stream().filter(item::equals).count();
        if (wearer != null) {
            assertEquals(1, live + onGround, item + " once, live or dropped");
            if (live == 1) assertEquals(1, wearer.count(item), item + " on its character's wearer");
            return;
        }
        String latest = null;
        for (PendingWrite write : writes) if (write.target().equals(file(character))) latest = write.content();
        boolean saved = latest != null && latest.contains(item.toString());
        assertEquals(0, live, item + " on no online body");
        assertEquals(1, (saved ? 1 : 0) + onGround, item + " once, saved or dropped");
    }
    @Test
    void aBodyLockedOutByAnEffectRemovalGetsNoneOfItsNewEffects() {
        PotionEffect speed = mock(PotionEffect.class, "speed");
        driver.effects = new ArrayList<>(List.of(speed));
        callOut = (body, what) -> {
            if (body == supporter && what.equals("clear") && Character.get(supporter.player) != null) lockOut(supporter);
        };

        assertEquals(SwapResult.INTERRUPTED, Puppets.swap(driver.player, supporter.player));

        assertFalse(supporter.effects.contains(speed));
    }

    @Test
    void aBodyLockedOutBeforeItsTurnStillHandsOffItsEffects() {
        PotionEffect speed = mock(PotionEffect.class, "speed"), glow = mock(PotionEffect.class, "glow");
        driver.effects = new ArrayList<>(List.of(speed));
        supporter.effects = new ArrayList<>(List.of(glow)); // the Driver gets these; their event locks the Supporter out
        driver.health = 7;
        callOut = (body, what) -> {
            if (body == driver && what.equals("effect") && Character.get(supporter.player) != null) lockOut(supporter);
        };

        assertEquals(SwapResult.INTERRUPTED, Puppets.swap(driver.player, supporter.player));

        assertTrue(supporter.effects.isEmpty(), "glow came off it, and speed never went on");
        assertNotEquals(7, supporter.health);
        assertEquals("tex-second-uniform", supporter.skin.getValue(), "main's skin isn't put on it either");
        verify(plugin.nametagManager, never()).showWornCharacter(supporter.player);
        assertTrue(driver.effects.contains(glow));
    }

    @Test
    void aBodyLockedOutByItsFirstNewEffectGetsNoMore() {
        PotionEffect speed = mock(PotionEffect.class, "speed"), glow = mock(PotionEffect.class, "glow");
        driver.effects = new ArrayList<>(List.of(speed, glow));
        driver.food = 3;
        callOut = (body, what) -> {
            if (body == supporter && what.equals("effect") && Character.get(supporter.player) != null) lockOut(supporter);
        };

        assertEquals(SwapResult.INTERRUPTED, Puppets.swap(driver.player, supporter.player));

        assertTrue(supporter.effects.contains(speed), "the one whose event locked it out");
        assertFalse(supporter.effects.contains(glow));
        assertNotEquals(3, supporter.food, "nor the rest of main's state");
    }

    @Test
    void aBodyLockedOutByItsPoseCommandIsntSeatedOnTheRide() {
        Plugin poses = mock(Plugin.class);
        when(poses.isEnabled()).thenReturn(true);
        when(poses.getConfig()).thenReturn(new YamlConfiguration());
        when(events.getPlugin("SneakyPoses")).thenReturn(poses);
        placeholders.when(PlaceholderUtil::isAvailable).thenReturn(true);
        placeholders.when(() -> PlaceholderUtil.setPlaceholders(any(Player.class), anyString())).thenAnswer(call ->
                String.valueOf(call.getArgument(0) == driver.player && call.getArgument(1).equals("%sneakyposes_crawling%")));
        when(world.getName()).thenReturn("world");
        Entity horse = mock(Entity.class, "horse");
        when(horse.isValid()).thenReturn(true);
        driver.vehicle = horse; // the Driver crawls on a horse: main's pose and ride both go to the Supporter
        bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
        bukkit.when(() -> Bukkit.dispatchCommand(any(), anyString())).thenAnswer(call -> {
            log.add("command:" + call.getArgument(1));
            if (((String) call.getArgument(1)).contains("Supporter")) lockOut(supporter);
            return true;
        });

        assertEquals(SwapResult.INTERRUPTED, Puppets.swap(driver.player, supporter.player));

        assertTrue(log.stream().anyMatch(line -> line.startsWith("command:crawl true Supporter")), log.toString());
        verify(horse, never()).addPassenger(supporter.player);
    }

    // I5 Last-played

    @Test
    void theDriversPointerNamesWhatTheyWearAndTheSupportersIsUntouched() {
        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        assertEquals(List.of(driverId + "/second"), api.pointerWrites());
    }

    @Test
    void aStaleLoadOfTheDriverStillOnItsWayIsDropped() {
        deferTasks = true;
        backend.submit(driver.player, new CharacterRequest.Select(driverId.toString(), "spare")); // read before the swap
        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));
        runDeferred();

        assertEquals(second, Character.get(driver.player).getKey(), "the pick read before the pointer moved isn't applied");
        assertEquals(List.of(driverId + "/spare", driverId + "/second"), api.pointerWrites(), "the swap's write lands last");
    }

    @Test
    void aFailedPointerWriteIsHandledAsAStorageFailure() {
        api.failPointers = true;

        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        verify(driver.player).sendMessage(any(Component.class)); // "Character storage unavailable ..."
        verify(supporter.player, never()).sendMessage(any(Component.class));
        // The Driver's storage is fenced until a restart, as after any uncertain write.
        backend.submit(driver.player, new CharacterRequest.Select(driverId.toString(), "spare"));
        assertEquals(second, Character.get(driver.player).getKey());
    }

    @Test
    void theDriverRejoinsAsTheCharacterTheyWear() {
        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));
        api.lastPlayed.put(driverId.toString(), "second"); // what the swap's write stored
        List<PuppetLeaveEvent> left = leaveEvents();

        new ConnectionEventListeners().onPlayerLeave(new PlayerQuitEvent(driver.player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));
        join(driver);

        assertEquals(second, Character.get(driver.player).getKey());
        assertEquals(main, Character.get(supporter.player).getKey(), "the Supporter keeps the Driver's former character");
        assertEquals(List.of(), left);
    }

    @Test
    void ifThePointerDidNotMoveTheDriversRejoinTakesTheCharacterBack() {
        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));
        // The fake keeps last-played at main, as if the write never landed.
        List<PuppetLeaveEvent> left = leaveEvents();

        new ConnectionEventListeners().onPlayerLeave(new PlayerQuitEvent(driver.player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));
        join(driver);

        assertEquals(main, Character.get(driver.player).getKey());
        assertEquals(supportersOwn, Character.get(supporter.player).getKey());
        assertEquals(1, left.size());
        assertSame(supporter.player, left.getFirst().getPlayer());
        assertEquals(main, left.getFirst().getCharacterKey());
        assertEquals(Reason.OWNER_JOINED, left.getFirst().getReason());
    }

    @Test
    void afterTheSwapTheSupporterQuittingEndsThePuppetOfTheNewCharacter() {
        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));
        List<PuppetLeaveEvent> left = leaveEvents();
        writes.clear();

        new ConnectionEventListeners().onPlayerLeave(new PlayerQuitEvent(supporter.player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertEquals(1, left.size());
        assertEquals(main, left.getFirst().getCharacterKey());
        assertEquals(Reason.QUIT, left.getFirst().getReason());
        assertEquals(List.of(file(main)), writes.stream().map(PendingWrite::target).toList(), "saved to the Driver's file");
    }

    // R1-R3 Refused: nothing moves

    @Test
    void refusalsChangeNothing() {
        assertEquals(SwapResult.SAME_BODY, Puppets.swap(driver.player, driver.player));
        third.offline();
        assertEquals(SwapResult.BODY_OFFLINE, Puppets.swap(driver.player, third.player));
        bukkit.when(() -> Bukkit.getPlayer(thirdId)).thenReturn(third.player);
        assertEquals(SwapResult.NOT_WEARING, Puppets.swap(driver.player, third.player), "the third body wears nothing");
        supporter.dead = true;
        assertEquals(SwapResult.DEAD, Puppets.swap(driver.player, supporter.player));
        supporter.dead = false;
        supporter.location = new Location(mock(World.class), 0, 0, 0);
        assertEquals(SwapResult.DIFFERENT_WORLDS, Puppets.swap(driver.player, supporter.player));
        supporter.location = new Location(driver.location.getWorld(), 0, 0, 0);
        driver.sleeping = true;
        assertEquals(SwapResult.SLEEPING, Puppets.swap(driver.player, supporter.player));
        driver.sleeping = false;
        when(plugin.getStorageMode()).thenReturn(StorageMode.LEGACY);
        assertEquals(SwapResult.UNSUPPORTED, Puppets.swap(driver.player, supporter.player));
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> Puppets.swap(driver.player, supporter.player));

        assertUnchanged();
        assertEquals(List.of(), writes);
    }

    @Test
    void aRefusedTeleportPutsBothBackMovingAsTheyWere() {
        Location driverSpot = driver.location.clone();
        driver.velocity = new Vector(0.3, -0.5, 0);
        driver.fallDistance = 4;
        supporter.refuseTeleport = true;

        assertEquals(SwapResult.REFUSED, Puppets.swap(driver.player, supporter.player));

        assertEquals(driverSpot, driver.location, "the Driver went back");
        assertEquals(new Vector(0.3, -0.5, 0), driver.velocity, "still moving as before the refused swap");
        assertEquals(4f, driver.fallDistance);
        assertUnchanged();
        assertFalse(log.stream().anyMatch(s -> s.startsWith("skin:") || s.startsWith("health")), log.toString());
    }

    @Test
    void aRedirectedTeleportIsARefusal() {
        Location driverSpot = driver.location.clone();
        Location supporterSpot = supporter.location.clone();
        listener = event -> {
            if (event instanceof PlayerTeleportEvent teleport && teleport.getPlayer() == supporter.player
                    && teleport.getTo().getX() == driverSpot.getX())
                teleport.setTo(new Location(world, 500, 70, 500)); // e.g. a region plugin
        };

        assertEquals(SwapResult.REFUSED, Puppets.swap(driver.player, supporter.player));

        assertEquals(driverSpot, driver.location);
        assertEquals(supporterSpot, supporter.location, "put back where it stood");
        assertUnchanged();
    }

    @Test
    void aBodyWithoutASkinPropertyHandsOverNone() {
        ProfileProperty mainSkin = driver.skin;
        supporter.skin = null; // e.g. an offline-mode account

        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));

        assertNull(driver.skin, "the Driver no longer shows main's skin");
        assertSame(mainSkin, supporter.skin);
    }

    @Test
    void theTopUpDoesntRefillSpentAbsorption() {
        driver.maxHealth = 30;
        driver.health = 28;
        driver.absorption = 4;
        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));
        supporter.maxHealth = 30;
        supporter.absorption = 1; // damage took absorption, not health

        nextTick();

        assertEquals(28, supporter.health);
        assertEquals(1, supporter.absorption);
    }

    @Test
    void aVetoedSkinRefusesTheSwapBeforeAnythingMoves() {
        Location driverSpot = driver.location.clone();
        skinVetoed.add(supporter.player); // a listener refuses main's skin on the Supporter

        assertEquals(SwapResult.REFUSED, Puppets.swap(driver.player, supporter.player));

        assertEquals(driverSpot, driver.location);
        assertFalse(log.stream().anyMatch(s -> s.startsWith("teleport") || s.startsWith("skin:")), log.toString());
        assertEquals(List.of(), writes, "refused before anything was saved");
        assertUnchanged();
    }

    @Test
    void healthAboveTheNewBodysMaximumIsToppedUpOnceItsEquipmentCounts() {
        driver.maxHealth = 30; // main's equipment gives it 30 maximum health
        driver.health = 28;

        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));
        assertEquals(20, supporter.health, "capped by the Supporter's maximum this tick");

        supporter.maxHealth = 30; // the equipment's bonus reaches the body on its next tick
        nextTick();
        assertEquals(28, supporter.health);
    }

    @Test
    void theTopUpLeavesHealthThatChangedOrABodyThatSwitchedAlone() {
        driver.maxHealth = 30;
        driver.health = 28;
        assertEquals(SwapResult.SWAPPED, Puppets.swap(driver.player, supporter.player));
        supporter.maxHealth = 30;
        supporter.health = 12; // hurt during that tick

        nextTick();

        assertEquals(12, supporter.health);
    }

    private void nextTick() {
        List<Runnable> due = new ArrayList<>(later);
        later.clear();
        due.forEach(Runnable::run);
    }

    private void assertUnchanged() {
        assertEquals(main, Character.get(driver.player).getKey());
        assertEquals(second, Character.get(supporter.player).getKey());
        assertSame(driver.player, CharacterHolders.holderOf(main));
        assertSame(supporter.player, CharacterHolders.holderOf(second));
        assertEquals(List.of(), api.pointerWrites());
    }

    private List<PuppetLeaveEvent> leaveEvents() {
        List<PuppetLeaveEvent> left = new ArrayList<>();
        listener = event -> { if (event instanceof PuppetLeaveEvent leave) left.add(leave); };
        return left;
    }

    private Body body(Object player) {
        if (player == driver.player) return driver;
        if (player == supporter.player) return supporter;
        return third;
    }

    private static ItemStack item(String name) {
        ItemStack item = mock(ItemStack.class, name);
        return item;
    }

    private Character character(CharacterKey key, Player wearer) {
        return new Character(key, wearer, key.characterId(), "", "", "", "", false, "", "", "");
    }

    /** Kicks a body: it goes offline, then SCM's quit listener runs, as on Paper. */
    private static void kick(Body body) {
        body.offline();
        new ConnectionEventListeners().onPlayerLeave(new PlayerQuitEvent(body.player, (Component) null, PlayerQuitEvent.QuitReason.KICKED));
    }

    /** Locks a body out of its characters, as SCM's character locks do: it stops wearing its character, unsaved. */
    private static void lockOut(Body body) {
        Character.remove(body.player, Reason.LOCKED_OUT);
    }

    private void runDeferred() {
        while (!deferred.isEmpty()) deferred.remove(0).run();
        deferTasks = false;
    }

    private void join(Body body) {
        bukkit.when(() -> Bukkit.getPlayer(body.player.getUniqueId())).thenReturn(body.player);
        backend.submit(body.player, new CharacterRequest.Join(body.player.getUniqueId().toString()));
    }

    private Path file(CharacterKey key) {
        return data.resolve(key.owner().toString()).resolve(key.characterId() + ".yml");
    }

    private static Location location(PendingWrite write) {
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.loadFromString(write.content()); } catch (Exception e) { throw new AssertionError(e); }
        return yaml.getLocation("location");
    }

    private void gameplayFile(CharacterKey key, double x) throws Exception {
        File file = file(key).toFile();
        Files.createDirectories(file.getParentFile().toPath());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("location", new Location(null, x, 64, 0));
        yaml.save(file);
    }
}
