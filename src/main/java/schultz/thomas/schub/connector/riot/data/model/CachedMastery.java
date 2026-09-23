package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.ChampionMastery;

import java.time.Instant;
import java.util.List;

@Document("riot_mastery")
public record CachedMastery(@Id String puuid, List<ChampionMastery> masteries, Instant fetchedAt) {
}
