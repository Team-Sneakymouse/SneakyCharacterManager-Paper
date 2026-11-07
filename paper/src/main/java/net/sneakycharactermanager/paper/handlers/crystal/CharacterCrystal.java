package net.sneakycharactermanager.paper.handlers.crystal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.sneakyutils.util.TextUtility;

import net.luckperms.api.model.user.User;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterSlots;
import net.sneakycharactermanager.paper.util.LuckPermsNodes;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;

/**
 * Character Crystal ({@code lom:character_crystal}, magic item {@code item-characterCrystal}). Right-click: the crystal
 * flies above the player, swells, pulls them in, grants one more character slot and swaps them to a new character.
 */
public final class CharacterCrystal implements Listener {

    public static final NamespacedKey ITEM_MODEL = new NamespacedKey("lom", "character_crystal");
    /** MagicSpells stores each magic item's name under magicspells:magicitem; only the real item counts. */
    public static final NamespacedKey MAGIC_ITEM_KEY = NamespacedKey.fromString("magicspells:magicitem");
    public static final String MAGIC_ITEM_NAME = "item-characterCrystal";

    private static final long CONFIRM_WINDOW_MS = 5000;
    private static final double MAX_DRIFT = 8.0;
    private static final int RISE_END = 20;
    private static final int EXPAND_END = 45;
    private static final int PULL_END = 70;
    private static final int COLLAPSE_END = 78;
    private static final double HOVER_HEIGHT = 3.2;
    private static final Color RUBY = Color.fromRGB(0xE0, 0x20, 0x38);
    private static final Color EMBER = Color.fromRGB(0xFF, 0x6A, 0x1A);
    private static final Color VIOLET = Color.fromRGB(0x8A, 0x30, 0xFF);

    private final SneakyCharacterManager plugin;
    /** Players whose save or ritual is in progress (value null while the LuckPerms save runs). */
    private final Map<UUID, Ritual> active = new HashMap<>();
    private final Map<UUID, Long> confirmUntil = new HashMap<>();

    public CharacterCrystal(SneakyCharacterManager plugin) {
        this.plugin = plugin;
    }

    public static boolean isCrystal(@Nullable ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return false;
        String name = item.getItemMeta().getPersistentDataContainer().get(MAGIC_ITEM_KEY, PersistentDataType.STRING);
        return MAGIC_ITEM_NAME.equals(name);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!isCrystal(item)) return;
        // Air clicks arrive already cancelled, so ignoreCancelled can't be used. Region and protection plugins deny
        // the item use instead, and that is respected here.
        if (event.useItemInHand() == Event.Result.DENY) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (active.containsKey(id)) return;
        if (!canUse(player)) return;

        long now = System.currentTimeMillis();
        Long until = confirmUntil.get(id);
        if (until == null || until < now) {
            confirmUntil.put(id, now + CONFIRM_WINDOW_MS);
            send(player, "&eRight-click again within 5 seconds to use your &cCharacter Crystal&e. It grants one more"
                    + " character slot and swaps you to a new character.");
            return;
        }
        confirmUntil.remove(id);

        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        String startCharacter = Character.get(player).getCharacterUUID();
        User user = LuckPermsNodes.user(player);
        int bonus = LuckPermsNodes.highestOwnPermanentSuffix(user, CharacterSlots.BONUS_PREFIX) + 1;
        int newMax = CharacterSlots.max(player) - CharacterSlots.bonus(player) + Math.max(CharacterSlots.bonus(player), bonus);

        // Save the slot first; the crystal is only spent once LuckPerms has stored it.
        active.put(id, null);
        CompletableFuture<Void> save;
        try {
            save = LuckPermsNodes.addPermanent(player, CharacterSlots.BONUS_PREFIX + bonus);
        } catch (IllegalStateException e) {
            active.remove(id);
            send(player, "&cThe crystal can't reach your soul right now. Try again in a moment.");
            return;
        }
        save.whenComplete((ok, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                active.remove(id);
                if (player.isOnline()) send(player, "&cThe crystal flickers and stays whole: your slot couldn't be"
                        + " saved. Your crystal was not used; please tell staff.");
                return;
            }
            plugin.getLogger().info("Character Crystal: " + player.getName() + " got bonus slot " + bonus + " ("
                    + newMax + " slots).");
            ItemStack shown = consume(player, hand);
            if (!player.isOnline()) {
                active.remove(id);
                return;
            }
            Ritual ritual = new Ritual(player, shown, newMax, startCharacter);
            active.put(id, ritual);
            ritual.runTaskTimer(plugin, 0L, 1L);
        }));
    }

    /** Takes one crystal (the hand used, else anywhere in the inventory) unless in creative; returns a copy to show. */
    private ItemStack consume(Player player, EquipmentSlot hand) {
        PlayerInventory inventory = player.getInventory();
        boolean spend = player.getGameMode() != GameMode.CREATIVE;
        ItemStack held = inventory.getItem(hand);
        ItemStack shown = null;
        if (isCrystal(held)) {
            shown = held.clone();
            if (spend) {
                held.setAmount(held.getAmount() - 1);
                inventory.setItem(hand, held.getAmount() <= 0 ? null : held);
            }
        } else {
            ItemStack[] contents = inventory.getContents();
            for (int i = 0; i < contents.length; i++) {
                if (!isCrystal(contents[i])) continue;
                shown = contents[i].clone();
                if (spend) {
                    contents[i].setAmount(contents[i].getAmount() - 1);
                    inventory.setItem(i, contents[i].getAmount() <= 0 ? null : contents[i]);
                }
                break;
            }
            if (shown == null) {
                plugin.getLogger().warning("Character Crystal: " + player.getName()
                        + " got a slot but no crystal was left to take (moved during the save).");
                shown = new ItemStack(Material.PAPER);
                ItemMeta meta = shown.getItemMeta();
                meta.setItemModel(ITEM_MODEL);
                shown.setItemMeta(meta);
            }
        }
        shown.setAmount(1);
        return shown;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        confirmUntil.remove(id);
        Ritual ritual = active.get(id);
        if (ritual != null) {
            active.remove(id);
            ritual.abort();
        }
    }

    /** Stops every ritual (plugin disable). Slots were saved before each ritual began. */
    public void shutdown() {
        for (Ritual ritual : active.values()) if (ritual != null) ritual.abort();
        active.clear();
    }

    private boolean canUse(Player player) {
        if (!plugin.luckPermsActive) {
            send(player, "&cThe crystal stays dark. (LuckPerms is not running, so no slot can be granted.)");
            return false;
        }
        if (!Character.isPlayedMapped(player)) {
            send(player, "&cYour character is still loading. Try again in a moment.");
            return false;
        }
        if (LuckPermsNodes.user(player) == null) {
            send(player, "&cThe crystal can't reach your soul right now. Try again in a moment.");
            return false;
        }
        return true;
    }

    private static void send(Player player, String message) {
        player.sendMessage(TextUtility.INSTANCE.convertToComponent(message));
    }

    private final class Ritual extends BukkitRunnable {
        private final Player player;
        private final int newMax;
        private final String startCharacter;
        private final ItemDisplay display;
        private final Location hover;
        private int tick = 0;
        private float spin = 0f;
        private boolean done = false;

        Ritual(Player player, ItemStack shown, int newMax, String startCharacter) {
            this.player = player;
            this.newMax = newMax;
            this.startCharacter = startCharacter;
            Location eye = player.getEyeLocation();
            Location start = eye.clone().add(eye.getDirection().multiply(0.6)).subtract(0, 0.35, 0);
            start.setYaw(0);
            start.setPitch(0);
            this.hover = player.getLocation().add(0, HOVER_HEIGHT, 0);
            this.hover.setYaw(0);
            this.hover.setPitch(0);
            World world = player.getWorld();
            this.display = world.spawn(start, ItemDisplay.class, d -> {
                d.setItemStack(shown);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setGlowing(true);
                d.setGlowColorOverride(RUBY);
                d.setPersistent(false);
                d.setTeleportDuration(RISE_END);
                d.setTransformation(transform(0.45f, 0f));
            });
            send(player, "&cThe crystal tears free of your grip and rises...");
            world.playSound(start, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1f, 0.6f);
            world.playSound(start, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.6f);
        }

        @Override
        public void run() {
            // Teleports, world changes or a character load end the ritual (the slot stays granted).
            if (!player.isOnline() || player.isDead() || !display.isValid()
                    || !player.getWorld().equals(hover.getWorld())
                    || player.getLocation().distanceSquared(hover) > MAX_DRIFT * MAX_DRIFT
                    || !onStartCharacter()) {
                active.remove(player.getUniqueId());
                abort();
                return;
            }
            World world = display.getWorld();
            if (tick == 1) display.teleport(hover);

            if (tick <= RISE_END) {
                spinTo(0.45f + 0.55f * tick / RISE_END, 0.25f);
                if (tick % 2 == 0) world.spawnParticle(Particle.DUST, display.getLocation(), 3, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(RUBY, 1.2f));
            } else if (tick <= EXPAND_END) {
                float p = (tick - RISE_END) / (float) (EXPAND_END - RISE_END);
                spinTo(1f + 2.2f * p, 0.25f + 0.35f * p);
                ring(world, 1.0 + 1.6 * p, tick * 0.35, EMBER);
                if (tick % 5 == 0) world.playSound(hover, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1f,
                        0.5f + p);
                if (tick == EXPAND_END) {
                    world.playSound(hover, Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS, 0.5f, 1.8f);
                    send(player, "&4It hungers for you.");
                }
            } else if (tick <= PULL_END) {
                float p = (tick - EXPAND_END) / (float) (PULL_END - EXPAND_END);
                spinTo(3.2f + 0.4f * (float) Math.sin(tick * 0.6), 0.65f);
                pull(p);
                stream(world);
                ring(world, 2.6 - 1.2 * p, -tick * 0.45, VIOLET);
                if (tick == EXPAND_END + 1) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0, false, false, false));
                }
                if (tick == PULL_END - 6) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));
                    world.playSound(hover, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 0.5f);
                }
            } else if (tick <= COLLAPSE_END) {
                float p = (tick - PULL_END) / (float) (COLLAPSE_END - PULL_END);
                spinTo(Math.max(0.01f, 3.6f * (1f - p)), 0.9f);
                player.setVelocity(new Vector(0, 0, 0));
                if (tick == PULL_END + 1) {
                    world.spawnParticle(Particle.DUST, hover, 60, 0.6, 0.6, 0.6, 0, new Particle.DustOptions(RUBY, 2f));
                    world.spawnParticle(Particle.END_ROD, hover, 40, 0.1, 0.1, 0.1, 0.25);
                    world.playSound(hover, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1f, 0.7f);
                }
            } else {
                finish();
                return;
            }
            tick++;
        }

        private void pull(float p) {
            Location target = hover.clone().subtract(0, 1.2, 0);
            Vector diff = target.toVector().subtract(player.getLocation().toVector());
            double strength = 0.12 + 0.3 * p;
            Vector v = diff.length() < 0.2 ? new Vector(0, 0, 0) : diff.multiply(strength);
            if (v.length() > 0.9) v.normalize().multiply(0.9);
            player.setVelocity(v);
        }

        private void stream(World world) {
            Location from = player.getLocation().add(0, 1, 0);
            Vector step = hover.toVector().subtract(from.toVector()).multiply(1 / 8.0);
            double phase = tick * 0.5;
            for (int i = 0; i < 8; i++) {
                double a = phase + i * 0.8;
                Location at = from.clone().add(step.clone().multiply(i)).add(Math.cos(a) * 0.4, 0, Math.sin(a) * 0.4);
                world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(i % 3 == 0 ? VIOLET : RUBY, 1.0f));
            }
            world.spawnParticle(Particle.PORTAL, from, 6, 0.4, 0.8, 0.4, 0.6);
        }

        private void ring(World world, double radius, double phase, Color color) {
            for (int i = 0; i < 4; i++) {
                double a = phase + i * Math.PI / 2;
                Location at = hover.clone().add(Math.cos(a) * radius, Math.sin(a * 0.5) * 0.3, Math.sin(a) * radius);
                world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.4f));
            }
        }

        private void spinTo(float scale, float speed) {
            spin += speed;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(2);
            display.setTransformation(transform(scale, spin));
        }

        private boolean onStartCharacter() {
            if (!Character.isPlayedMapped(player)) return false;
            Character current = Character.get(player);
            return current != null && startCharacter.equals(current.getCharacterUUID());
        }

        private void finish() {
            cancel();
            active.remove(player.getUniqueId());
            done = true;
            display.remove();
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0);
            if (!onStartCharacter()) {
                send(player, "&eYour character changed during the ritual, so you weren't swapped. Your new slot is"
                        + " yours: open &f/char&e to use it.");
                return;
            }
            send(player, "&aA new soul takes shape. You now have &f" + newMax + "&a character slots.");
            send(player, "&aUse &f/nick&a and &f/skin&a to shape your new character!");
            ProxyMessagingUtil.sendByteArray(player, "createNewCharacter", player.getUniqueId().toString());
        }

        void abort() {
            if (done) return;
            done = true;
            if (!isCancelled()) cancel();
            if (display.isValid()) display.remove();
            if (player.isOnline()) {
                send(player, "&eThe crystal's ritual was interrupted, but your new character slot is yours. Open &f/char&e to use it.");
            }
        }
    }

    private static Transformation transform(float scale, float spin) {
        Quaternionf left = new Quaternionf().rotateY(spin).rotateX(0.25f * (float) Math.sin(spin * 0.5f));
        return new Transformation(new Vector3f(), left, new Vector3f(scale, scale, scale), new Quaternionf());
    }
}
