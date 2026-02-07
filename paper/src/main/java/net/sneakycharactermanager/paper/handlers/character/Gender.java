package net.sneakycharactermanager.paper.handlers.character;

public enum Gender {
	MASCULINE,
	FEMININE,
	NONBINARY;

	public static Gender fromString(String value) {
		if (value == null || value.isEmpty()) return null;
		return switch (value.toLowerCase()) {
			case "feminine" -> FEMININE;
			case "nonbinary" -> NONBINARY;
			case "masculine" -> MASCULINE;
			default -> null;
		};
	}

	public String toConfigKey() {
		return switch (this) {
			case FEMININE -> "feminine";
			case NONBINARY -> "nonbinary";
			default -> "masculine";
		};
	}

	public static String toConfigKeyNullable(Gender gender) {
		return gender == null ? "" : gender.toConfigKey();
	}
}

