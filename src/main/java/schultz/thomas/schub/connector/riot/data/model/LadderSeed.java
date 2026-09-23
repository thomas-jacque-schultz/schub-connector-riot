package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Un joueur tiré au hasard dans les classements : ses parties donnent une population de palier connu.
@Document("riot_ladder_seed")
@CompoundIndex(name = "group_sampledAt", def = "{'group': 1, 'sampledAt': -1}")
public record LadderSeed(@Id String puuid, String group, String tier, String division, int leaguePoints,
                         Instant sampledAt) {
}
