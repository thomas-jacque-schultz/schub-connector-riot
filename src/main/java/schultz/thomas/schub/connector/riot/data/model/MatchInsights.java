package schultz.thomas.schub.connector.riot.data.model;

import java.time.Instant;
import java.util.List;

public record MatchInsights(
        String matchId,
        boolean timelineAvailable,
        Instant ranksObservedAt,
        EarlyGame early,
        List<EndObjectives> endObjectives,
        List<Proximity> proximities,
        List<Isolation> isolations,
        List<Contest> contests,
        List<Participant> participants
) {

    public record Participant(
            String puuid,
            int side,
            TeamPosition position,
            int championId,
            RankedStanding solo,
            RankedStanding flex,
            At15 at15,
            AtEnd atEnd
    ) {
    }

    public record AtEnd(int gold, int xp, int kills) {
    }

    public record EndObjectives(int side, int dragons, int heralds) {
    }

    public record At15(int gold, int xp, int cs, int damageToChampions, int kills, int deaths, int assists) {
    }
}
