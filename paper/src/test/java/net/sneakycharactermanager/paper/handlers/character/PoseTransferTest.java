package net.sneakycharactermanager.paper.handlers.character;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import net.sneakycharactermanager.paper.handlers.character.PoseTransfer.Pose;
import net.sneakycharactermanager.paper.handlers.character.PoseTransfer.Type;

/**
 * The spot and console command {@link PoseTransfer} gives SneakyPoses, so its seat lands exactly where the old one
 * was (SneakyPoses adds its offsets to the spot again). {@code SwapTest} checks the commands run after the skin.
 */
class PoseTransferTest {

    final World world = mock(World.class);

    PoseTransferTest() {
        when(world.getName()).thenReturn("world");
    }

    @Test
    void aSitIsPutBackWithTheSeatAtTheSameSpotAndFacing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("sit.y-offset", 0.25);
        Location seat = new Location(world, 10.5, 64.75, -3.25, 90f, 0f);

        Location spot = PoseTransfer.spot(Type.SIT, seat, config);

        assertEquals(new Location(world, 10.5, 64.5, -3.25, 90f, 0f), spot); // SneakyPoses adds 0.25 again
        assertEquals("sit true Supporter world,10.5,64.5,-3.25,90.0", PoseTransfer.command(new Pose(Type.SIT, spot), "Supporter"));
    }

    @Test
    void aSleepUsesBothOfItsOffsetsAndTheExactFiveSpotForm() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("sleep.y-offset", 0.25);
        config.set("sleep.camera-y-offset", -0.5);
        Location seat = new Location(world, 1.25, 63.75, 2.5, 180f, 0f); // body at spot + 0.25, seat 0.5 below that

        Location spot = PoseTransfer.spot(Type.SLEEP, seat, config);

        assertEquals(64.0, spot.getY());
        assertEquals("sleep true Driver world,1.25,64.0,2.5,180.0", PoseTransfer.command(new Pose(Type.SLEEP, spot), "Driver"));
    }

    @Test
    void missingSettingsUseSneakyPosesDefaults() {
        Location seat = new Location(world, 0, 64, 0);

        assertEquals(64.5, PoseTransfer.spot(Type.SIT, seat, new YamlConfiguration()).getY()); // sit.y-offset -0.5
        assertEquals(64.5, PoseTransfer.spot(Type.SLEEP, seat, new YamlConfiguration()).getY()); // -0.1 + -0.4
    }

    @Test
    void aCrawlNeedsNoSpot() {
        assertEquals("crawl true Driver", PoseTransfer.command(new Pose(Type.CRAWL, null), "Driver"));
    }

    @Test
    void onlySneakyPosesSeatsArePoseSeats() {
        Entity seat = mock(Entity.class), horse = mock(Entity.class);
        when(seat.getScoreboardTags()).thenReturn(Set.of("SneakyPosesSeat"));
        when(horse.getScoreboardTags()).thenReturn(Set.of());

        assertTrue(PoseTransfer.isSeat(seat));
        assertFalse(PoseTransfer.isSeat(horse));
        assertFalse(PoseTransfer.isSeat(null));
    }
}
