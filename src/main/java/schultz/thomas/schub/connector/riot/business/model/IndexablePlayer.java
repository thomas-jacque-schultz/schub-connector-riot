package schultz.thomas.schub.connector.riot.business.model;

// featured : joueur apex récent ou créateur de contenu, en tête du plan du site.
public record IndexablePlayer(String gameName, String tagLine, boolean featured) {
}
