package net.sneakycharactermanager.paper.storage;

/** Typed operations at the transport boundary. The legacy protocol remains an adapter only. */
public sealed interface CharacterRequest {
    record Join(String owner) implements CharacterRequest {}
    record ListCharacters(String owner, String requester) implements CharacterRequest {}
    record Select(String owner, String character) implements CharacterRequest {}
    record SelectByName(String owner, String name) implements CharacterRequest {}
    record Temporary(String requester, String owner, String character) implements CharacterRequest {}
    record Create(String owner) implements CharacterRequest {}
    record Update(String owner, String character, int type, Object[] values) implements CharacterRequest {}
    record DefaultSkin(String owner, String character, String url, boolean slim) implements CharacterRequest {}
    record Template(String id, String name, String url, String skinUuid, boolean slim) implements CharacterRequest {}
    record Uniform(String base, String hash, String uuid, String url, String texture, String signature) implements CharacterRequest {}
    record ResolveSkin(String player, String requestId, String source, boolean slim) implements CharacterRequest {}
    record RegisterSkin(String id, String url, String texture, String signature, String source) implements CharacterRequest {}
    record Titles(String player) implements CharacterRequest {}
    record CreateTitle(String admin, String target, String code, String display) implements CharacterRequest {}
    record Search(String requester, String filter) implements CharacterRequest {}
    record Quit(String owner) implements CharacterRequest {}

    static CharacterRequest decode(String operation, Object... a) {
        return switch (operation) {
            case "playerJoin" -> new Join((String)a[0]);
            case "playerQuit" -> new Quit((String)a[0]);
            case "syncCharacters" -> new ListCharacters((String)a[0], (String)a[1]);
            case "selectCharacter" -> new Select((String)a[0], (String)a[1]);
            case "selectCharacterByName" -> new SelectByName((String)a[0], (String)a[1]);
            case "tempCharacter" -> new Temporary((String)a[0], (String)a[1], (String)a[2]);
            case "createNewCharacter" -> new Create((String)a[0]);
            case "updateCharacter" -> new Update((String)a[0], (String)a[1], (Integer)a[2], java.util.Arrays.copyOfRange(a, 3, a.length));
            case "defaultSkin" -> new DefaultSkin((String)a[0], (String)a[1], (String)a[2], (Boolean)a[3]);
            case "saveTemplateChar" -> new Template((String)a[0], (String)a[1], (String)a[2], (String)a[3], (Boolean)a[4]);
            case "saveUniformVariant" -> new Uniform((String)a[0], (String)a[1], (String)a[2], (String)a[3], (String)a[4], (String)a[5]);
            case "resolveSkin" -> new ResolveSkin((String)a[0], (String)a[1], (String)a[2], (Boolean)a[3]);
            case "registerSkin" -> new RegisterSkin((String)a[0], (String)a[1], (String)a[2], (String)a[3], (String)a[4]);
            case "requestTitles" -> new Titles((String)a[0]);
            case "createTitle" -> new CreateTitle((String)a[0], (String)a[1], (String)a[2], (String)a[3]);
            case "getAllCharacters" -> new Search((String)a[0], (String)a[1]);
            default -> throw new IllegalArgumentException("Unknown SCM operation: " + operation);
        };
    }
}
