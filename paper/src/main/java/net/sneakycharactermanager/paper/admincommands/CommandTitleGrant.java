package net.sneakycharactermanager.paper.admincommands;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.sneakyutils.util.TextUtility;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.titles.TitlePermissionGranter;
import net.sneakycharactermanager.paper.handlers.titles.TitleRegistry;
import net.sneakycharactermanager.paper.handlers.titles.TitleSelectionMenu;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;

public class CommandTitleGrant extends CommandBaseAdmin {

	public CommandTitleGrant() {
		super("titlegrant");
		this.description = "Create or reuse a title, grant it to a player, and equip it on their current character.";
		this.usageMessage = "/titlegrant <player>";
	}

	@Override
	public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
		if (!(sender instanceof Player admin)) {
			sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&4Must be a player to run this command"));
			return false;
		}

		if (args.length != 1) {
			sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&4Invalid Usage: " + this.usageMessage));
			return false;
		}

		Player target = Bukkit.getPlayer(args[0]);
		if (target == null) {
			sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&aUnknown player: &b" + args[0]));
			return false;
		}

		if (!TitlePermissionGranter.isAvailable()) {
			sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&4LuckPerms is required to grant titles."));
			return false;
		}

		Character character = Character.get(target);
		if (character == null) {
			sender.sendMessage(TextUtility.INSTANCE.convertToComponent(
					"&aThis player is not currently on a character: &b" + args[0]));
			return false;
		}

		openForm(admin, target.getUniqueId(), "");
		return true;
	}

	private void openForm(Player admin, UUID targetId, String initialDisplay) {
		Player target = Bukkit.getPlayer(targetId);
		String targetName = target == null ? "player" : target.getName();

		ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(30)).build();
		UUID adminId = admin.getUniqueId();

		ActionButton save = ActionButton.builder(Component.text("Grant title")).width(150)
				.action(DialogAction.customClick((response, audience) -> {
					String rawDisplay = response.getText("display");
					Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
						Player adminPlayer = Bukkit.getPlayer(adminId);
						if (adminPlayer == null) {
							return;
						}
						handleSubmit(adminPlayer, targetId, rawDisplay);
					});
				}, once)).build();

		ActionButton cancel = ActionButton.builder(Component.text("Cancel")).width(150).build();

		DialogInput displayInput = DialogInput.text("display", Component.text("Display text"))
				.width(260)
				.maxLength(64)
				.initial(initialDisplay == null ? "" : initialDisplay)
				.build();

		Dialog dialog = Dialog.create(b -> b.empty()
				.base(DialogBase.builder(Component.text("Grant title to " + targetName))
						.canCloseWithEscape(true)
						.body(List.of(DialogBody.plainMessage(
								Component.text("Enter a title to unlock and equip. Existing titles keep their original display text."),
								280)))
						.inputs(List.of(displayInput))
						.build())
				.type(DialogType.confirmation(save, cancel)));

		admin.showDialog(dialog);
	}

	static String titleKeyFromDisplay(String display) {
		return TitleRegistry.normalizeTitleKey(display);
	}

	private void handleSubmit(Player admin, UUID targetId, String rawDisplay) {
		String code = titleKeyFromDisplay(rawDisplay);
		String display = rawDisplay == null ? "" : rawDisplay.trim();
		String enteredDisplay = rawDisplay == null ? "" : rawDisplay;

		if (code.isEmpty()) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent(
					"&cDisplay text must contain letters or numbers to generate a title key."));
			openForm(admin, targetId, enteredDisplay);
			return;
		}

		if (display.isEmpty()) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent("&cDisplay text cannot be empty."));
			openForm(admin, targetId, enteredDisplay);
			return;
		}

		Player target = Bukkit.getPlayer(targetId);
		if (target == null) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent("&cThat player is no longer online."));
			return;
		}

		if (Character.get(target) == null) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent(
					"&cThat player is no longer on a character."));
			return;
		}

		if (!TitlePermissionGranter.isAvailable()) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent("&4LuckPerms is required to grant titles."));
			return;
		}

		ProxyMessagingUtil.sendByteArray(admin, "createTitle",
				admin.getUniqueId().toString(),
				targetId.toString(),
				code,
				display);
		admin.sendMessage(TextUtility.INSTANCE.convertToComponent("&7Resolving title on proxy..."));
	}

	public static void handleCreateResult(
			String adminUUID,
			String targetUUID,
			boolean created,
			String code,
			String display,
			String error) {
		Player admin = Bukkit.getPlayer(UUID.fromString(adminUUID));
		if (admin == null) {
			return;
		}

		UUID targetId = UUID.fromString(targetUUID);
		if (!created) {
			String reason = switch (error == null ? "" : error) {
				case "invalid" -> "&cCould not create title (invalid code or display).";
				default -> "&cCould not create title &b" + code + "&c.";
			};
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent(reason));
			CommandTitleGrant command = new CommandTitleGrant();
			command.openForm(admin, targetId, display);
			return;
		}

		Player target = Bukkit.getPlayer(targetId);
		if (target == null) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent(
					"&aResolved title &b" + display + "&a (&b" + code
							+ "&a), but the player is no longer online to grant it."));
			return;
		}

		Character character = Character.get(target);
		if (character == null) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent(
					"&aResolved title &b" + display + "&a (&b" + code
							+ "&a), but the player is no longer on a character."));
			return;
		}

		if (!TitlePermissionGranter.isAvailable()) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent(
					"&aResolved title &b" + display + "&a (&b" + code
							+ "&a), but LuckPerms is unavailable to grant it."));
			return;
		}

		try {
			TitlePermissionGranter.grant(target, code);
		} catch (IllegalStateException e) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent(
					"&cTitle resolved, but failed to grant LuckPerms permission: " + e.getMessage()));
			return;
		}

		TitleSelectionMenu.applyTitle(target, character, code);

		admin.sendMessage(TextUtility.INSTANCE.convertToComponent(
				"&aResolved title &b" + display + "&a (&b" + code + "&a), granted to &b"
						+ target.getName() + "&a, and equipped on their current character."));
		target.sendMessage(TextUtility.INSTANCE.convertToComponent(
				"&aYou were granted the title &b" + display + "&a."));
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
		if (args.length == 1) {
			List<String> playerNames = new ArrayList<>();
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())
						&& !player.getName().equals("CMI-Fake-Operator")) {
					playerNames.add(player.getName());
				}
			}
			return playerNames;
		}
		return new ArrayList<>();
	}
}
