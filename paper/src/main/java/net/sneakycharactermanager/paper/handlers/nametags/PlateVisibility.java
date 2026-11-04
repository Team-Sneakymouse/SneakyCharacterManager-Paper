package net.sneakycharactermanager.paper.handlers.nametags;

import org.bukkit.entity.Player;

/** Which viewers see a plate. Pure: reads visibility state, changes nothing. */
final class PlateVisibility {

    private PlateVisibility() {}

    /**
     * <ol>
     *   <li>viewer excluded by the attachment: hidden</li>
     *   <li>attachment parked: hidden</li>
     *   <li>plate on an anchor: shown iff the viewer can see the anchor (a passenger whose vehicle is hidden would
     *       float client-side at a stale position)</li>
     *   <li>plate on the owner: shown iff the viewer can see the owner</li>
     * </ol>
     */
    static boolean visibleTo(Player viewer, Player owner, PlateAttachment attachment) {
        if (attachment == null) return viewer.canSee(owner);
        if (attachment.excludes(viewer.getUniqueId())) return false;
        if (attachment.parked() || attachment.anchor() == null) return false;
        return viewer.canSee(attachment.anchor());
    }
}
