package net.sneakycharactermanager.common.titles;

/**
 * Shared title-code normalization for Paper and proxy.
 */
public final class TitleKeys {

	private TitleKeys() {}

	/**
	 * Normalize a title code: trim, lowercase, spaces/hyphens to underscores,
	 * strip non {@code [a-z0-9_]}, collapse and trim underscores.
	 */
	public static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		String s = raw.trim().toLowerCase()
				.replace(' ', '_')
				.replace('-', '_');
		StringBuilder out = new StringBuilder(s.length());
		boolean lastUnderscore = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				out.append(c);
				lastUnderscore = false;
			} else if (c == '_') {
				if (!lastUnderscore && out.length() > 0) {
					out.append('_');
					lastUnderscore = true;
				}
			}
		}
		while (out.length() > 0 && out.charAt(out.length() - 1) == '_') {
			out.setLength(out.length() - 1);
		}
		return out.toString();
	}
}
