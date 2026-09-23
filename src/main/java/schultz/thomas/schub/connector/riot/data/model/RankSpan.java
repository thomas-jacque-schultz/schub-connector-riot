package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;

import java.time.Instant;
import java.util.Objects;

// Riot ne sert que le rang courant : un rang non relevé à l'époque d'une partie est perdu.
@Document(RankSpan.COLLECTION)
@CompoundIndex(name = "puuid_queue_lastSeenAt", def = "{'puuid': 1, 'queue': 1, 'lastSeenAt': -1}")
public record RankSpan(
        @Id String id,
        String puuid,
        QueueKind queue,
        String tier,
        String division,
        int leaguePoints,
        Instant firstSeenAt,
        Instant lastSeenAt
) {

    public static final String COLLECTION = "riot_rank_history";

    public boolean sameRank(RankedStanding standing) {
        return Objects.equals(tier, standing.tier()) && Objects.equals(division, standing.division());
    }

    public RankSpan seenAt(Instant instant, int points) {
        Instant premier = instant.isBefore(firstSeenAt) ? instant : firstSeenAt;
        boolean plusRecent = instant.isAfter(lastSeenAt);
        return new RankSpan(id, puuid, queue, tier, division, plusRecent ? points : leaguePoints, premier,
                plusRecent ? instant : lastSeenAt);
    }
}
