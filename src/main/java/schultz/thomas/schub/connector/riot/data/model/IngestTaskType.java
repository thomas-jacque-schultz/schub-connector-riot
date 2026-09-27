package schultz.thomas.schub.connector.riot.data.model;

public enum IngestTaskType {

    PLAYER_IDS,
    // Joueur recherché : ses dernières parties d'abord, le reste de son historique en fond.
    PLAYER_PREVIEW,
    // Même chose au-delà du budget du visiteur : les parties arrivent au compte-gouttes.
    PLAYER_PREVIEW_SLOW,

    MATCH_DETAIL,
    MATCH_TIMELINE,
    MATCH_TIMELINE_DIGEST,
    MATCH_RANKS,

    LADDER_PAGE,
    SEED_MATCHES
}
