package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Un document par fenêtre, portée et poste ; les fenêtres passées restent, une partie se note avec celle de son patch.
@Document("riot_reference")
@CompoundIndex(name = "scope_position_computedAt", def = "{'scope': 1, 'position': 1, 'computedAt': -1}")
public record StoredReference(
        @Id String id,
        List<String> patches,
        String scope,
        String position,
        Instant computedAt,
        String distribution,
        List<Double> percentiles,
        Map<String, Grid> metrics
) {

    public record Grid(Map<String, TierGrid> tiers, List<Double> ladder, List<String> missingTiers) {
    }

    public record TierGrid(long count, List<Double> values) {
    }
}
