package net.sneakycharactermanager.paper.admincommands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;
import net.sneakycharactermanager.paper.util.ChatUtility;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CommandSkinQueue extends CommandBaseAdmin {

    private static final String[] PRIO_LABELS = {"Preload", "Online", "Load", "Skin", "Uniform"};

    public CommandSkinQueue() {
        super("skinqueue");
        this.description = "Admin commands for the MineSkin skin queue.";
        this.setUsage("/skinqueue <status|detail <p>|flush>");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatUtility.convertToComponent("&cUsage: " + this.usageMessage));
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> handleStatus(sender);
            case "detail" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatUtility.convertToComponent("&cUsage: /skinqueue detail <0-4>"));
                    return false;
                }
                try {
                    int p = Integer.parseInt(args[1]);
                    handleDetail(sender, p);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatUtility.convertToComponent("&cInvalid priority. Specify 0-4."));
                }
            }
            case "flush" -> handleFlush(sender);
            default -> sender.sendMessage(ChatUtility.convertToComponent("&cUnknown subcommand. Usage: " + this.usageMessage));
        }
        return true;
    }

    private void handleStatus(CommandSender sender) {
        SkinQueue q = SneakyCharacterManager.getInstance().skinQueue;
        Map<Integer, List<SkinData>> queue = q.getQueue();

        long now = System.currentTimeMillis();
        long nextReset = q.getNextReset();
        String resetStr = (nextReset > 0 && nextReset > now) ? ((nextReset - now) / 1000) + "s" : "Ready";
        String delayStr = (q.getDelayMillis() / 1000.0f) + "s";

        sender.sendMessage(ChatUtility.convertToComponent("&8&m                                        "));
        sender.sendMessage(ChatUtility.convertToComponent("&b&lSkin Queue"));
        sender.sendMessage(ChatUtility.convertToComponent(
            "&7Capacity: &f" + q.getRemaining() + "/" + q.getLimit() +
            "  &7Reset: &f" + resetStr +
            "  &7Delay: &f" + delayStr
        ));
        sender.sendMessage(ChatUtility.convertToComponent("&7Click a tier to see details:"));

        // One line per P-tier, each clickable
        for (int p = SkinQueue.PRIO_UNIFORM; p >= SkinQueue.PRIO_PRELOAD; p--) {
            List<SkinData> list = queue.getOrDefault(p, Collections.emptyList());
            int size = list.size();
            long processing = list.stream().filter(SkinData::isProcessing).count();

            NamedTextColor col = size == 0 ? NamedTextColor.DARK_GRAY : NamedTextColor.YELLOW;
            String label = PRIO_LABELS[p];

            Component tier = Component.text("  P" + p + " " + label + " [" + size + "]", col)
                .clickEvent(ClickEvent.runCommand("/skinqueue detail " + p))
                .hoverEvent(HoverEvent.showText(
                    Component.text(size + " waiting", NamedTextColor.WHITE)
                        .appendNewline()
                        .append(Component.text(processing + " processing", NamedTextColor.GREEN))
                        .appendNewline()
                        .append(Component.text("Click for details", NamedTextColor.GRAY, TextDecoration.ITALIC))
                ));
            sender.sendMessage(tier);
        }
        sender.sendMessage(ChatUtility.convertToComponent("&8&m                                        "));
    }

    private void handleDetail(CommandSender sender, int p) {
        if (p < SkinQueue.PRIO_PRELOAD || p > SkinQueue.PRIO_UNIFORM) {
            sender.sendMessage(ChatUtility.convertToComponent("&cPriority must be 0-" + SkinQueue.PRIO_UNIFORM + "."));
            return;
        }

        SkinQueue q = SneakyCharacterManager.getInstance().skinQueue;
        List<SkinData> list = q.getQueue().getOrDefault(p, Collections.emptyList());
        String label = PRIO_LABELS[p];

        sender.sendMessage(ChatUtility.convertToComponent("&8&m                                        "));
        sender.sendMessage(ChatUtility.convertToComponent("&b&lP" + p + " &e" + label + " &7(" + list.size() + " entries)"));
        sender.sendMessage(ChatUtility.convertToComponent("&8&m                                        "));

        if (list.isEmpty()) {
            sender.sendMessage(ChatUtility.convertToComponent("&7(empty)"));
        } else {
            for (SkinData data : list) {
                String state    = data.isProcessing() ? "&aprocessing" : "&7waiting";
                String jobPart  = data.hasJobId() ? " &8[&fjob:" + data.getJobId().substring(0, 8) + "...&8]" : "";
                String menuPart = data.isMenuOpen() ? " &d[menu]" : "";
                String url      = data.getUrl();
                String shortUrl = url.length() > 40 ? "..." + url.substring(url.length() - 40) : url;
                String charName = data.getCharacterName() != null ? data.getCharacterName() : "?";

                sender.sendMessage(ChatUtility.convertToComponent(
                    "&f" + data.getPlayer().getName() + " &8| &7" + charName + " &8| " + state + jobPart + menuPart
                ));
                sender.sendMessage(ChatUtility.convertToComponent("  &8└ &7" + shortUrl));
            }
        }
        sender.sendMessage(ChatUtility.convertToComponent("&8&m                                        "));
    }

    private void handleFlush(CommandSender sender) {
        SkinQueue q = SneakyCharacterManager.getInstance().skinQueue;
        Map<Integer, List<SkinData>> queue = q.getQueue();

        int total = queue.values().stream().mapToInt(List::size).sum();
        queue.values().forEach(List::clear);

        sender.sendMessage(ChatUtility.convertToComponent(
            "&aFlushed &f" + total + " &aentr" + (total == 1 ? "y" : "ies") + " from the skin queue."
        ));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, org.bukkit.Location location) {
        if (args.length == 1) return Arrays.asList("status", "detail", "flush");
        if (args.length == 2 && args[0].equalsIgnoreCase("detail"))
            return Arrays.asList("0", "1", "2", "3", "4");
        return Collections.emptyList();
    }
}
