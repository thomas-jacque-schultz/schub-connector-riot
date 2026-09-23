package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;

import java.time.Instant;
import java.util.List;

// Pas d'index d'expiration : une entrée périmée datée vaut mieux qu'une erreur quand Riot ne répond pas.
@Document("riot_ranking")
public record CachedRanking(@Id String puuid, List<RankedStanding> standings, Instant fetchedAt) {
}
