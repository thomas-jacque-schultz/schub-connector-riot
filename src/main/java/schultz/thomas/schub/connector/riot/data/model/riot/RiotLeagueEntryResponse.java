package schultz.thomas.schub.connector.riot.data.model.riot;

/**
 * Forme brute de {@code league-v4} — route <strong>plateforme</strong>.
 *
 * <p>Relevé le 18-09 sur {@code /lol/league/v4/entries/by-puuid/{puuid}} : la réponse porte
 * désormais le {@code puuid} et plus de {@code summonerId}. Le détour par {@code summoner-v4}
 * qu'imposaient les anciens exemples n'a plus lieu d'être — c'est un appel économisé par
 * joueur et par relevé.</p>
 */
public record RiotLeagueEntryResponse(
        String puuid,
        String queueType,
        String tier,
        String rank,
        int leaguePoints,
        int wins,
        int losses,
        boolean hotStreak,
        boolean veteran,
        boolean freshBlood,
        boolean inactive
) {
}
