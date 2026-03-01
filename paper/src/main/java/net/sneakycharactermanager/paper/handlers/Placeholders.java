package net.sneakycharactermanager.paper.handlers;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.Gender;
import net.sneakycharactermanager.paper.handlers.skins.SkinQueue;

public class Placeholders extends PlaceholderExpansion {

	public Placeholders() {
	}

	@Override
	public @NotNull String getIdentifier() {
		return SneakyCharacterManager.IDENTIFIER;
	}

	@Override
	public @NotNull String getAuthor() {
		return SneakyCharacterManager.AUTHORS;
	}

	@Override
	public @NotNull String getVersion() {
		return SneakyCharacterManager.VERSION;
	}

	@Override
	public boolean persist() {
		return true;
	}

	@Override
	public String onPlaceholderRequest(Player player, String params) {
		String placeholder = params.toLowerCase();
		SkinQueue sq = SneakyCharacterManager.getInstance().skinQueue;

		// Global Skin Queue Placeholders
		if (placeholder.equals("skinqueue_remaining")) {
			return String.valueOf(sq.getRemaining());
		} else if (placeholder.equals("skinqueue_limit")) {
			return String.valueOf(sq.getLimit());
		} else if (placeholder.equals("skinqueue_reset")) {
			return String.valueOf(sq.getNextReset());
		} else if (placeholder.equals("skinqueue_reset_formatted")) {
			long now = System.currentTimeMillis();
			long nextReset = sq.getNextReset();
			return (nextReset > 0 && nextReset > now) ? ((nextReset - now) / 1000) + "s" : "Ready";
		} else if (placeholder.equals("skinqueue_delay")) {
			return String.valueOf(sq.getDelayMillis());
		} else if (placeholder.equals("skinqueue_processing")) {
			return String.valueOf(sq.getProcessingCount());
		} else if (placeholder.equals("skinqueue_total_queued")) {
			return String.valueOf(sq.getTotalQueuedCount());
		} else if (placeholder.startsWith("skinqueue_queued_p")) {
			try {
				int p = Integer.parseInt(placeholder.substring("skinqueue_queued_p".length()));
				return String.valueOf(sq.getQueuedCount(p));
			} catch (NumberFormatException e) {
				return "0";
			}
		}

		// Character Specific Placeholders (Require a Player)
		if (player == null) return null;
		Character character = Character.get(player);
		if (character == null) return "";

		if (placeholder.equals("character_uuid")) {
			return character.getCharacterUUID();
		} else if (placeholder.equals("character_name")) {
			return character.getName();
		} else if (placeholder.equals("character_name_noformat")) {
			return character.getNameUnformatted();
		} else if (placeholder.equals("character_skin")) {
			return character.getSkin();
		} else if (placeholder.equals("character_slim")) {
			return character.isSlim() + "";
		} else if (placeholder.equals("character_tags")) {
			return character.getTagsAsString();
		} else if (placeholder.equals("character_name_prefix")) {
			return character.getNamePrefix();
		} else if (placeholder.equals("character_gender")) {
			return Gender.toConfigKeyNullable(character.getGender());
		} else if (placeholder.equals("character_gender_suffix")) {
			return character.getGenderSuffix();
		} else if (placeholder.equals("character_pronoun_s")) {
			return pronoun(character.getGender(), "s");
		} else if (placeholder.equals("character_pronoun_o")) {
			return pronoun(character.getGender(), "o");
		} else if (placeholder.equals("character_pronoun_p")) {
			return pronoun(character.getGender(), "p");
		} else if (placeholder.equals("character_pronoun_p2")) {
			return pronoun(character.getGender(), "p2");
		} else if (placeholder.startsWith("character_hastag_")) {
			return character.hasTag(placeholder.replace("character_hastag_", "")) + "";
		} else if (placeholder.startsWith("character_tag_")) {
			return character.tagValue(placeholder.replace("character_tag_", ""));
		}

		return null;
	}

	private String pronoun(Gender gender, String kind) {
		if (gender == null) gender = Gender.NONBINARY;
		return switch (kind) {
			case "s" -> switch (gender) {
				case MASCULINE -> "he";
				case FEMININE -> "she";
				default -> "they";
			};
			case "o" -> switch (gender) {
				case MASCULINE -> "him";
				case FEMININE -> "her";
				default -> "them";
			};
			case "p" -> switch (gender) {
				case MASCULINE -> "his";
				case FEMININE -> "her";
				default -> "their";
			};
			case "p2" -> switch (gender) {
				case MASCULINE -> "his";
				case FEMININE -> "hers";
				default -> "theirs";
			};
			default -> "";
		};
	}

}
