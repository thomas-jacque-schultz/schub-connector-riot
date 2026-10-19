package schultz.thomas.schub.connector.riot.data.model;

public record RankedStanding(
        QueueKind queue,
        String riotQueueType,
        String tier,
        String division,
        int leaguePoints,
        int wins,
        int losses,
        boolean hotStreak,
        boolean inactive,
        java.time.Instant observedAt
) {
}
