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
import net.sneakycharactermanager.paper.api.character.CharacterKey;
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

		openForm(admin, target.getUniqueId(), character.getKey(), "");
		return true;
	}

	/** {@code character} is what the target wore when the admin started; the title is only equipped on that. */
	private void openForm(Player admin, UUID targetId, CharacterKey character, String initialDisplay) {
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
						handleSubmit(adminPlayer, targetId, character, rawDisplay);
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

	/** Character a pending grant should be equipped on. The round trip carries no character, so a result is matched
	 *  to its request by admin, target and title code; an identical grant can't be submitted while one is resolving. */
	private record PendingEquip(CharacterKey character, long since) {}
	private static final java.util.Map<String, PendingEquip> PENDING_EQUIPS = new java.util.concurrent.ConcurrentHashMap<>();
	/** A result that never arrives must not block the same grant forever. */
	private static final long PENDING_EXPIRY_MILLIS = 60_000;

	private static String pendingKey(String admin, String target, String code) {
		return admin + "|" + target + "|" + TitleRegistry.normalizeTitleKey(code == null ? "" : code);
	}

	static String titleKeyFromDisplay(String display) {
		return TitleRegistry.normalizeTitleKey(display);
	}

	private void handleSubmit(Player admin, UUID targetId, CharacterKey character, String rawDisplay) {
		String code = titleKeyFromDisplay(rawDisplay);
		String display = rawDisplay == null ? "" : rawDisplay.trim();
		String enteredDisplay = rawDisplay == null ? "" : rawDisplay;

		if (code.isEmpty()) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent(
					"&cDisplay text must contain letters or numbers to generate a title key."));
			openForm(admin, targetId, character, enteredDisplay);
			return;
		}

		if (display.isEmpty()) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent("&cDisplay text cannot be empty."));
			openForm(admin, targetId, character, enteredDisplay);
			return;
		}

		Player target = Bukkit.getPlayer(targetId);
		if (target == null) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent("&cThat player is no longer online."));
			return;
		}

		if (!Character.isWearing(target, character)) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent(
					"&cThat player is no longer on the same character. Run /titlegrant again."));
			return;
		}

		if (!TitlePermissionGranter.isAvailable()) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent("&4LuckPerms is required to grant titles."));
			return;
		}

		String pendingKey = pendingKey(admin.getUniqueId().toString(), targetId.toString(), code);
		PendingEquip earlier = PENDING_EQUIPS.get(pendingKey);
		if (earlier != null && System.currentTimeMillis() - earlier.since() < PENDING_EXPIRY_MILLIS) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent(
					"&cThat title is still being granted to this player. Wait for it to finish."));
			return;
		}
		PENDING_EQUIPS.put(pendingKey, new PendingEquip(character, System.currentTimeMillis()));
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
		PendingEquip pending = PENDING_EQUIPS.remove(pendingKey(adminUUID, targetUUID, code)); // also when the admin left
		CharacterKey character = pending == null ? null : pending.character();
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
			if (character != null) new CommandTitleGrant().openForm(admin, targetId, character, display);
			return;
		}

		Player target = Bukkit.getPlayer(targetId);
		if (target == null) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent(
					"&aResolved title &b" + display + "&a (&b" + code
							+ "&a), but the player is no longer online to grant it."));
			return;
		}

		// Equip only on the character the grant started for; the permission itself belongs to the account (D4).
		boolean equip = character != null && Character.isWearing(target, character);

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

		if (!equip) {
			admin.sendMessage(TextUtility.INSTANCE.convertToComponent(
					"&aResolved title &b" + display + "&a (&b" + code + "&a) and granted it to &b" + target.getName()
							+ "&a, but did not equip it: they switched character."));
			return;
		}
		TitleSelectionMenu.applyTitle(target, Character.get(target), code);

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
