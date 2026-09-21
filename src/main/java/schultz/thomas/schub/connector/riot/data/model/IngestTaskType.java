package schultz.thomas.schub.connector.riot.data.model;

public enum IngestTaskType {

    /** Relever les identifiants de parties d'un joueur. Clé : le puuid. */
    PLAYER_IDS,

    /** Récupérer le détail d'une partie. Clé : le matchId. C'est l'appel qui coûte. */
    MATCH_DETAIL
}
