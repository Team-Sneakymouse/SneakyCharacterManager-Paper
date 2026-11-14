package net.sneakycharactermanager.paper.handlers.nametags;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.MockedStatic;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;

/**
 * Drives the real {@link NametagManager} (and listeners built on it) against mocked Bukkit boundaries: a tick-driven
 * scheduler, players with per-viewer {@code canSee} state, and plates whose display entity records mounts, per-viewer
 * hide/show and text packets. Only the NMS packet construction inside {@link NicknameEntity} is replaced.
 */
public final class NameplateFixture implements AutoCloseable {

    public final SneakyCharacterManager plugin = mock(SneakyCharacterManager.class);
    public final World world = mock(World.class, "world");
    public final World otherWorld = mock(World.class, "otherWorld");
    public final YamlConfiguration config = new YamlConfiguration();
    public final NametagManager manager;

    private final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    private final MockedStatic<SneakyCharacterManager> instance = mockStatic(SneakyCharacterManager.class);
    private final MockedStatic<Character> characters = mockStatic(Character.class);

    private final Map<UUID, Player> online = new LinkedHashMap<>();
    private final Map<UUID, Character> characterOf = new HashMap<>();
    private final Map<UUID, Set<UUID>> hiddenFromViewer = new HashMap<>();
    private final Map<UUID, String> vanish = new HashMap<>();
    private final List<Task> tasks = new ArrayList<>();
    private long tick;
    public boolean primaryThread = true;
    /** Entities that refuse the plate as a passenger. */
    public final Set<UUID> refusesPassengers = new HashSet<>();
    public final List<Plate> plates = new ArrayList<>();
    /** When set, the next plate construction throws after creating its entity. */
    public RuntimeException failNextPlate;

    record Task(long due, Runnable runnable) {}

    /** One built plate: the NicknameEntity seam plus its world display entity. */
    public static final class Plate {
        public Nickname nickname;
        public final NicknameEntity entity;
        public final TextDisplay display;
        public final UUID displayId = UUID.randomUUID();
        public Entity vehicle;
        public boolean removed;
        public final Set<Player> trackers = new LinkedHashSet<>();

        Plate(NicknameEntity entity, TextDisplay display) {
            this.entity = entity;
            this.display = display;
        }
    }

    public NameplateFixture() {
        config.set("see-own-nameplate", false);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getName()).thenReturn("SneakyCharacterManager");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("NameplateFixture"));
        instance.when(SneakyCharacterManager::getInstance).thenReturn(plugin);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            Task task = new Task(tick + (long) invocation.getArgument(2), invocation.getArgument(1));
            tasks.add(task);
            BukkitTask handle = mock(BukkitTask.class);
            doAnswer(cancel -> tasks.remove(task)).when(handle).cancel();
            return handle;
        });
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            tasks.add(new Task(tick + 1, invocation.getArgument(1)));
            return mock(BukkitTask.class);
        });
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> List.copyOf(online.values()));
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(invocation -> online.get(invocation.<UUID>getArgument(0)));
        bukkit.when(Bukkit::isPrimaryThread).thenAnswer(invocation -> primaryThread);

        manager = new NametagManager(plugin, this::buildPlate, player -> vanish.get(player.getUniqueId()));
        plugin.nametagManager = manager;
    }

    private Nickname buildPlate(Player owner, String name, String title) {
        NicknameEntity entity = mock(NicknameEntity.class);
        TextDisplay display = mock(TextDisplay.class);
        Plate plate = new Plate(entity, display);

        when(entity.display()).thenReturn(display);
        when(entity.isValid()).thenAnswer(invocation -> !plate.removed);
        when(entity.mountOn(any(Entity.class))).thenAnswer(invocation -> {
            Entity vehicle = invocation.getArgument(0);
            if (refusesPassengers.contains(vehicle.getUniqueId())) return vehicle.equals(plate.vehicle);
            plate.vehicle = vehicle;
            return true;
        });
        doAnswer(invocation -> { plate.removed = true; plate.vehicle = null; return null; }).when(entity).destroy();
        when(display.getUniqueId()).thenReturn(plate.displayId);
        when(display.isValid()).thenAnswer(invocation -> !plate.removed);
        when(display.getWorld()).thenAnswer(invocation -> plate.vehicle == null ? owner.getWorld() : plate.vehicle.getWorld());
        when(display.getLocation()).thenAnswer(invocation -> new Location(display.getWorld(), 0, 64, 0));
        when(display.getTrackedBy()).thenAnswer(invocation -> Set.copyOf(plate.trackers));
        plates.add(plate);
        // Spawned and mounted on the owner, as NicknameEntity's constructor does.
        plate.vehicle = owner;
        if (failNextPlate != null) {
            // Fails inside Nickname's constructor (packet build), after the entity exists.
            doThrow(failNextPlate).when(entity).updatePackets(any(), any());
            failNextPlate = null;
        }
        plate.nickname = new Nickname(owner, name, title, player -> entity);
        return plate.nickname;
    }

    public Player player(String name) {
        Player player = mock(Player.class, name);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenAnswer(invocation -> online.get(id) == player);
        when(player.isValid()).thenAnswer(invocation -> online.get(id) == player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenAnswer(invocation -> new Location(player.getWorld(), 0, 64, 0));
        when(player.canSee((Player) any())).thenAnswer(invocation -> canSee(player, invocation.getArgument(0)));
        when(player.canSee((Entity) any())).thenAnswer(invocation -> canSee(player, invocation.getArgument(0)));
        online.put(id, player);
        return player;
    }

    /** A player with a loaded character, as SCM sees it after join. */
    public Player character(String name) {
        Player player = player(name);
        Character character = mock(Character.class);
        when(character.getDisplayName()).thenReturn(name + " (character)");
        characters.when(() -> Character.get(player)).thenReturn(character);
        characterOf.put(player.getUniqueId(), character);
        return player;
    }

    public Character characterOf(Player player) {
        return characterOf.get(player.getUniqueId());
    }

    public void removeCharacter(Player player) {
        characters.when(() -> Character.get(player)).thenReturn(null);
    }

    public Entity anchor(String name) {
        Entity anchor = mock(Entity.class, name);
        UUID id = UUID.randomUUID();
        when(anchor.getUniqueId()).thenReturn(id);
        when(anchor.isValid()).thenReturn(true);
        when(anchor.getWorld()).thenReturn(world);
        return anchor;
    }

    private boolean canSee(Player viewer, Entity target) {
        if (target == null) return false;
        if (viewer.getUniqueId().equals(target.getUniqueId())) return true;
        return !hiddenFromViewer.getOrDefault(viewer.getUniqueId(), Set.of()).contains(target.getUniqueId());
    }

    /** Another plugin (or vanish) hides {@code target} from {@code viewer}; Paper updates canSee before its event. */
    public void hide(Player viewer, Entity target) {
        hiddenFromViewer.computeIfAbsent(viewer.getUniqueId(), id -> new HashSet<>()).add(target.getUniqueId());
    }

    public void show(Player viewer, Entity target) {
        hiddenFromViewer.getOrDefault(viewer.getUniqueId(), new HashSet<>()).remove(target.getUniqueId());
    }

    public void vanish(Player player, String placeholderValue) {
        vanish.put(player.getUniqueId(), placeholderValue);
    }

    public void quit(Player player) {
        online.remove(player.getUniqueId());
    }

    /** Advances one server tick and runs whatever became due (tasks scheduled meanwhile wait for later ticks). */
    public void tick() {
        tick++;
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = tasks.iterator(); it.hasNext(); ) {
            Task task = it.next();
            if (task.due() <= tick) {
                due.add(task);
                it.remove();
            }
        }
        due.forEach(task -> task.runnable().run());
    }

    public void ticks(int count) {
        for (int i = 0; i < count; i++) tick();
    }

    public int pendingTasks() {
        return tasks.size();
    }

    /** Viewers SCM currently hides {@code owner}'s plate from with its own key. */
    public Set<UUID> hiddenFrom(Player owner) {
        Nickname nickname = manager.getNickname(owner);
        return nickname == null ? Set.of() : Set.copyOf(nickname.hiddenFrom);
    }

    public Plate plate(Player owner) {
        Nickname nickname = manager.getNickname(owner);
        if (nickname == null) return null;
        return plates.stream().filter(p -> p.nickname == nickname).findFirst().orElseThrow();
    }

    public List<Plate> platesOf(Predicate<Plate> filter) {
        return plates.stream().filter(filter).toList();
    }

    /** Nicks every given player as SCM would after their character loaded. */
    public void nick(Player... owners) {
        for (Player owner : owners) manager.nicknamePlayer(owner, owner.getName() + " (character)");
    }

    @Override
    public void close() {
        characters.close();
        instance.close();
        bukkit.close();
    }
}
