package schultz.thomas.schub.connector.riot.api.dto;

// Riot rend une chaîne vide quand il n'a pas su trancher (remake, custom, déconnexion) : UNKNOWN.
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
