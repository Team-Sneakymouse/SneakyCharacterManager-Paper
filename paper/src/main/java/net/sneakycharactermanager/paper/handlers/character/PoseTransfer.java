package net.sneakycharactermanager.paper.handlers.character;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import com.sneakyutils.util.PlaceholderUtil;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

/**
 * Moves a SneakyPoses pose (sit, sleep, crawl) from one body to the other in a swap. A teleport ends a pose
 * (SneakyPoses' {@code end-on-teleport}, on by default), so the swap reads each body's pose first and puts it back
 * on the other body afterwards, at the same spot, once that body wears the new skin (a sleeping pose copies it).
 *
 * <p>SneakyPoses has no API, so this goes through what it documents (its README, at {@code 8a4066c}): the
 * {@code %sneakyposes_sitting|sleeping|crawling%} placeholders, the {@code SneakyPosesSeat} tag on the seat it
 * spawns, the {@code sit.y-offset}, {@code sleep.y-offset} and {@code sleep.camera-y-offset} settings, and the console
 * form {@code /<pose> true <player> world,x,y,z,yaw}. The spot is worked back from the seat, which SneakyPoses puts at
 * the pose's spot plus those offsets. If any of that changes, or SneakyPoses or PlaceholderAPI is missing, the pose
 * isn't moved: the character simply stands up, as on any teleport.
 */
@ApiStatus.Internal
public final class PoseTransfer {

    static final String PLUGIN = "SneakyPoses";
    static final String SEAT_TAG = "SneakyPosesSeat";

    enum Type { SIT, SLEEP, CRAWL }

    /** A pose to put back: its type, and for a sit or sleep the spot to pass to SneakyPoses' command. */
    record Pose(Type type, @Nullable Location spot) {}

    private PoseTransfer() {}

    /** {@code body}'s current pose, or null if it has none (or it can't be read). */
    @Nullable
    static Pose capture(Player body) {
        Plugin poses = sneakyPoses();
        if (poses == null || !PlaceholderUtil.isAvailable()) return null;
        Type type = posing(body, "sitting") ? Type.SIT : posing(body, "sleeping") ? Type.SLEEP
                : posing(body, "crawling") ? Type.CRAWL : null;
        if (type == null) return null;
        if (type == Type.CRAWL) return new Pose(type, null);
        Entity seat = body.getVehicle();
        if (!isSeat(seat)) return null;
        return new Pose(type, spot(type, seat.getLocation(), poses.getConfig()));
    }

    /** True for the seat SneakyPoses spawns under a sitting or sleeping body. */
    static boolean isSeat(@Nullable Entity vehicle) {
        return vehicle != null && vehicle.getScoreboardTags().contains(SEAT_TAG);
    }

    /**
     * The spot SneakyPoses' command needs to put its seat exactly at {@code seat} again, facing the seat's way. A sit
     * spawns its seat at the spot plus {@code sit.y-offset}; a sleep moves the body to the spot plus
     * {@code sleep.y-offset} and spawns the seat {@code sleep.camera-y-offset} below that. Defaults are SneakyPoses'.
     */
    static Location spot(Type type, Location seat, ConfigurationSection config) {
        double below = switch (type) {
            case SIT -> config.getDouble("sit.y-offset", -0.5);
            case SLEEP -> config.getDouble("sleep.y-offset", -0.1) + config.getDouble("sleep.camera-y-offset", -0.4);
            case CRAWL -> 0;
        };
        Location spot = seat.clone().subtract(0, below, 0);
        spot.setPitch(0);
        return spot;
    }

    /** SneakyPoses' console command that puts {@code pose} on {@code playerName}. */
    static String command(Pose pose, String playerName) {
        String name = pose.type().name().toLowerCase(Locale.ROOT);
        Location spot = pose.spot();
        if (spot == null) return name + " true " + playerName;
        return name + " true " + playerName + " " + spot.getWorld().getName() + "," + spot.getX() + "," + spot.getY()
                + "," + spot.getZ() + "," + spot.getYaw();
    }

    /** Puts {@code pose} on {@code body}, which already stands at its spot. Does nothing for no pose. */
    static void apply(@Nullable Pose pose, Player body) {
        if (pose == null || sneakyPoses() == null) return;
        String command = command(pose, body.getName());
        try {
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                SneakyCharacterManager.getInstance().getLogger().warning("SneakyPoses didn't take '" + command + "'");
            }
        } catch (RuntimeException e) {
            SneakyCharacterManager.getInstance().getLogger().warning("Couldn't put a pose back with '" + command + "': " + e);
        }
    }

    private static boolean posing(Player body, String pose) {
        return "true".equals(PlaceholderUtil.setPlaceholders(body, "%sneakyposes_" + pose + "%"));
    }

    @Nullable
    private static Plugin sneakyPoses() {
        Plugin poses = Bukkit.getPluginManager().getPlugin(PLUGIN);
        return poses != null && poses.isEnabled() ? poses : null;
    }
}
