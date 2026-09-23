package schultz.thomas.schub.connector.riot.data.model.riot;

// league-v4 by-puuid rend le puuid et plus de summonerId (relevé le 18-09) : pas de détour par summoner-v4.
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
