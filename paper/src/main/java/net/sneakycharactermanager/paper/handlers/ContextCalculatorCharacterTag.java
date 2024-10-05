package net.sneakycharactermanager.paper.handlers;

import java.util.Set;
import java.util.HashSet;

import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;
import net.sneakycharactermanager.paper.handlers.character.Character;

public class ContextCalculatorCharacterTag implements ContextCalculator<Player> {

	private static final String CONTEXT_NAME = "charactertag";

	@Override
	public void calculate(@NonNull Player target, @NonNull ContextConsumer consumer) {
		Character character = Character.get(target);

		if (character != null) {
			for (String tag : character.getTags().keySet()) {
				consumer.accept(CONTEXT_NAME,
						character.tagValue(tag).isEmpty() || character.tagValue(tag).toUpperCase().equals("TRUE") ? tag
								: tag + ":" + character.tagValue(tag));
			}
		}
	}

	@Override
	public ContextSet estimatePotentialContexts() {
		ImmutableContextSet.Builder builder = ImmutableContextSet.builder();

		Set<String> allTags = new HashSet<>();

		for (Character character : Character.getAll()) {
			for (String tag : character.getTags().keySet()) {
				allTags.add(
						character.tagValue(tag).isEmpty() || character.tagValue(tag).toUpperCase().equals("TRUE") ? tag
								: tag + ":" + character.tagValue(tag));
			}
		}

		for (String tag : allTags) {
			builder.add(CONTEXT_NAME, tag);
		}

		return builder.build();
	}

	public void register() {
		LuckPerms luckPerms = LuckPermsProvider.get();
		luckPerms.getContextManager().registerCalculator(this);
	}

}