package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;

import java.time.Instant;
import java.util.Map;
import java.util.List;

/**
 * Les classements des dix joueurs, relevés à la collecte. Riot ne sert que le rang courant : pour
 * une partie ancienne, l'écart entre {@code observedAt} et la partie dit ce que vaut le relevé.
 */
@Document("riot_match_rank")
public record MatchRankSnapshot(@Id String matchId, Map<String, List<RankedStanding>> byPuuid,
                                Instant observedAt) {
}
