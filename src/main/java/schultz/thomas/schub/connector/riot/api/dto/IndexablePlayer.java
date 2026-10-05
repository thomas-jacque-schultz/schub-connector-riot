package schultz.thomas.schub.connector.riot.api.dto;

// featured : joueur apex récent ou créateur de contenu, en tête du plan du site.
public record IndexablePlayer(String gameName, String tagLine, boolean featured) {
}
