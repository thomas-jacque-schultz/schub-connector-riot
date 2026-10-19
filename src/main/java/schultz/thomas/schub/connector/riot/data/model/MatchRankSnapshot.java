package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Les classements des dix joueurs, relevés à la collecte. Riot ne sert que le rang courant : pour
 * une partie ancienne, l'écart entre {@code observedAt} et la partie dit ce que vaut le relevé.
 */
@Document("riot_match_rank")
public record MatchRankSnapshot(@Id String matchId, Map<String, List<RankedStanding>> byPuuid,
                                Instant observedAt) {
}
