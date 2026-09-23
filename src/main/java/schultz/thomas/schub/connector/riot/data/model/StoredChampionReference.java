package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Un document par champion et par fenêtre ; seuls les groupes de paliers assez peuplés y figurent.
@Document("riot_champion_reference")
@CompoundIndex(name = "championId_computedAt", def = "{'championId': 1, 'computedAt': -1}")
public record StoredChampionReference(
        @Id String id,
        int championId,
        List<String> patches,
        Instant computedAt,
        List<Double> percentiles,
        Map<String, Map<String, StoredReference.TierGrid>> metrics
) {
}
