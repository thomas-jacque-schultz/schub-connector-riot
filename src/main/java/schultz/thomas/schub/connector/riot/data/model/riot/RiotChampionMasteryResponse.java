package schultz.thomas.schub.connector.riot.data.model.riot;

public record RiotChampionMasteryResponse(
        String puuid,
        int championId,
        int championLevel,
        int championPoints,
        long lastPlayTime
) {
}
