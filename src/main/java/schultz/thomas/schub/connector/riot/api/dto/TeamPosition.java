package schultz.thomas.schub.connector.riot.api.dto;

/**
 * Le poste tenu dans la partie.
 *
 * <p>Riot renvoie {@code teamPosition} en majuscules, et une chaîne vide quand il n'a pas su
 * trancher (remake, partie personnalisée, déconnexion précoce). {@link #UNKNOWN} porte ce cas
 * plutôt que de le laisser remonter en {@code null} chez l'appelant.</p>
 */
public enum TeamPosition {

    TOP,
    JUNGLE,
    MIDDLE,
    BOTTOM,
    UTILITY,
    UNKNOWN;

    public static TeamPosition fromRiot(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        return switch (raw.toUpperCase()) {
            case "TOP" -> TOP;
            case "JUNGLE" -> JUNGLE;
            case "MIDDLE", "MID" -> MIDDLE;
            case "BOTTOM", "BOT" -> BOTTOM;
            case "UTILITY", "SUPPORT" -> UTILITY;
            default -> UNKNOWN;
        };
    }
}
