package net.sneakycharactermanager.paper.handlers.crystal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu;
import net.sneakycharactermanager.paper.handlers.character.CharacterSlots;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;

/**
 * Character Crystal ({@code lom:character_crystal}, magic item {@code item-characterCrystal}). Right-click: the crystal
 * flies above the player, swells, pulls them in, grants one more character slot and swaps them to a new character.
 */
public final class CharacterCrystal implements Listener {

    public static final NamespacedKey ITEM_MODEL = new NamespacedKey("lom", "character_crystal");

    private static final int RISE_END = 20;
    private static final int EXPAND_END = 45;
    private static final int PULL_END = 70;
    private static final int COLLAPSE_END = 78;
    private static final double HOVER_HEIGHT = 3.2;
    private static final Color RUBY = Color.fromRGB(0xE0, 0x20, 0x38);
    private static final Color EMBER = Color.fromRGB(0xFF, 0x6A, 0x1A);
    private static final Color VIOLET = Color.fromRGB(0x8A, 0x30, 0xFF);

    private final SneakyCharacterManager plugin;
    private final Map<UUID, Ritual> active = new HashMap<>();

    public CharacterCrystal(SneakyCharacterManager plugin) {
        this.plugin = plugin;
    }

    public static boolean isCrystal(@Nullable ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasItemModel() && ITEM_MODEL.equals(meta.getItemModel());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!isCrystal(item)) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (active.containsKey(player.getUniqueId())) return;
        if (!canUse(player)) return;

        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        ItemStack shown = item.clone();
        shown.setAmount(1);
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItem(hand, item.getAmount() <= 0 ? null : item);

        int newMax = grantSlot(player);
        Ritual ritual = new Ritual(player, shown, newMax);
        active.put(player.getUniqueId(), ritual);
        ritual.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Ritual ritual = active.remove(event.getPlayer().getUniqueId());
        if (ritual != null) ritual.abort();
    }

    /** Stops every ritual (plugin disable). Slots were granted when each ritual began. */
    public void shutdown() {
        for (Ritual ritual : active.values()) ritual.abort();
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
        if (CharacterSlots.isUnlimited(player)) {
            send(player, "&eYou already have unlimited character slots; the crystal has nothing to give you.");
            return false;
        }
        if (LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId()) == null) {
            send(player, "&cThe crystal can't reach your soul right now. Try again in a moment.");
            return false;
        }
        return true;
    }

    /** Adds {@code characterslots.<current+1>} to the player in LuckPerms; returns the new slot count. */
    private int grantSlot(Player player) {
        int newMax = CharacterSlots.parseMax(player) + 1;
        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        user.data().add(Node.builder(CharacterSelectionMenu.CHARACTER_SLOTS_PERMISSION_NODE + newMax).build());
        luckPerms.getUserManager().saveUser(user);
        plugin.getLogger().info("Character Crystal: " + player.getName() + " now has " + newMax + " character slots.");
        return newMax;
    }

    private static void send(Player player, String message) {
        player.sendMessage(TextUtility.INSTANCE.convertToComponent(message));
    }

    private final class Ritual extends BukkitRunnable {
        private final Player player;
        private final int newMax;
        private final ItemDisplay display;
        private final Location hover;
        private int tick = 0;
        private float spin = 0f;
        private boolean done = false;

        Ritual(Player player, ItemStack shown, int newMax) {
            this.player = player;
            this.newMax = newMax;
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
            if (!player.isOnline() || player.isDead() || !display.isValid()) {
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

        private void finish() {
            cancel();
            active.remove(player.getUniqueId());
            done = true;
            display.remove();
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0);
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
