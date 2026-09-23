package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.MetricReference;

import java.time.Instant;
import java.util.List;

// Un seul document : les référentiels par palier et par poste, recalculés depuis riot_player_position.
@Document("riot_metric_scale")
public record StoredMetricScale(@Id String id, Instant computedAt, List<MetricReference> leagues) {

    public static final String CURRENT = "current";
}
