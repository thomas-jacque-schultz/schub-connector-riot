package schultz.thomas.schub.connector.riot.data.model.riot;

/**
 * Forme brute de {@code champion-mastery-v4} — route <strong>plateforme</strong>.
 *
 * <p>{@code lastPlayTime} est en millisecondes depuis l'époque.</p>
 */
public record RiotChampionMasteryResponse(
        String puuid,
        int championId,
        int championLevel,
        int championPoints,
        long lastPlayTime
) {
}
