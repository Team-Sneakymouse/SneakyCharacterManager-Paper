package net.sneakycharactermanager.paper.commands;

import java.util.*;
import java.time.Duration;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;

import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterNickChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.CharacterUpdates;
import com.sneakyutils.util.TextUtility;

public class CommandNick extends CommandBase {
    private static final String CANCELLED = "The name change was cancelled. Please choose another name or contact staff.";

    public CommandNick() {
        super("nick");
        this.description = "Change the name of your character!";
        this.usageMessage = "/nick [Name]";
        this.setPermission(SneakyCharacterManager.IDENTIFIER + ".command." + this.getName());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtility.INSTANCE.convertToComponent("&4Must be a player to run this command"));
            return false;
        }

        if (args.length == 0) {
            Character character = Character.get(player);
            if (character == null) {
                player.sendMessage(TextUtility.INSTANCE.convertToComponent("&cSorry! Failed to retrieve character!"));
                return false;
            }
            openForm(player, character.getKey(), character.getName(), null);
            return true;
        }

        String nickname = String.join(" ", args);
        String error = changeNickname(player, nickname);
        if (error != null) {
            if (!CANCELLED.equals(error)) {
                player.sendMessage(TextUtility.INSTANCE.convertToComponent("&4" + error));
            }
            return false;
        }
        return true;
    }

    private String changeNickname(Player player, String nickname) {
        if (nickname.isBlank()) {
            return "Your name cannot be empty. Enter a character name.";
        }
        if (nickname.length() > 32 && !player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".formatnames")) {
            return "That name is too long! No more than 32 characters.";
        }

        List<String> bannedWords = SneakyCharacterManager.getInstance().getConfig().getStringList("bannedWords");

        boolean containsBannedWord = false;
        if(!bannedWords.isEmpty()){
            for(String word : bannedWords){
                if(nickname.toLowerCase().contains(word.toLowerCase())){
                    containsBannedWord = true;
                    break;
                }
            }
        }

        if(containsBannedWord){
            return "Your name contains a banned word! *Insert Ban Message Here*";
        }

        // Name Filtering (skipped when runner has formatnames)
        if (!player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".formatnames")) {
            String pattern = "^.*[^áéíóúýÁÉÍÓÚÝàèìòùÀÈÌÒÙâêîôûÂÊÎÔÛäëïöüÿÄËÏÖÜŸãñõÃÑÕçÇåÅčďěňřšťžČĎĚŇŘŠŤŽąęĄĘÆØæø\\w\\-\\'\\\"\\`<>&\\. ].*$";
            if (nickname.matches(pattern) || nickname.matches("^.*[<>&].*$")) {
                return "Invalid name! It cannot contain special characters! Quotes, Spaces, and Diacritics are okay.";
            }
        }

        Character character = Character.get(player);
        if(character == null) {
            return "Sorry! Failed to retrieve character!";
        }

        CharacterNickChangeEvent event = new CharacterNickChangeEvent(player, character.getKey(), character.getName(), nickname);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return CANCELLED;

        character.setName(nickname);
        SneakyCharacterManager.getInstance().nametagManager.nicknamePlayer(player, character.getDisplayName());
        CharacterUpdates.send(player, character.getKey(), CharacterUpdates.NAME, nickname);
        player.sendMessage(TextUtility.INSTANCE.convertToComponent("&eName updated to: " + nickname));
        return null;
    }

    void handleSubmit(Player player, CharacterKey character, String raw) {
        String draft = raw == null ? "" : raw;
        String error;
        Character current = Character.get(player);
        if (!player.hasPermission(getPermission())) {
            error = "You no longer have permission to use /nick.";
        } else if (current == null) {
            error = "Sorry! Failed to retrieve character!";
        } else if (!character.equals(current.getKey())) {
            error = "Your character changed while this form was open. Close it and run /nick again.";
        } else {
            error = changeNickname(player, draft);
        }
        if (error != null) openForm(player, character, draft, error);
    }

    static int inputMaxLength(boolean formatNames, String draft) {
        // Native text inputs only support a positive integer maximum, not live regex validation.
        // The codec also rejects initial text longer than that maximum: retain oversized drafts
        // after a permission change and enforce the actual 32-character rule on submit.
        return formatNames ? Integer.MAX_VALUE : Math.max(32, draft.length());
    }

    void openForm(Player player, CharacterKey character, String draft, String error) {
        UUID playerId = player.getUniqueId();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1)
                .lifetime(Duration.ofMinutes(30)).build();
        ActionButton save = ActionButton.builder(Component.text("Save name")).width(150)
                .action(DialogAction.customClick((response, audience) -> {
                    String raw = response.getText("nickname");
                    Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                        Player current = Bukkit.getPlayer(playerId);
                        if (current != null) handleSubmit(current, character, raw);
                    });
                }, once)).build();
        ActionButton cancel = ActionButton.builder(Component.text("Cancel")).width(150).build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Change character name"))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                error == null ? "Enter your character's name." : error), 300)))
                        .inputs(List.of(DialogInput.text("nickname", Component.text("Name"))
                                .width(260).maxLength(inputMaxLength(
                                        player.hasPermission(SneakyCharacterManager.IDENTIFIER + ".formatnames"), draft))
                                .initial(draft).build()))
                        .build())
                .type(DialogType.confirmation(save, cancel)));
        player.showDialog(dialog);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        return new ArrayList<>();
    }

}
