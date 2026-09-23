package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.MetricScale;

// Un seul document : l'échelle courante, recalculée depuis riot_participation.
@Document("riot_metric_scale")
public record StoredMetricScale(@Id String id, MetricScale scale) {

    public static final String CURRENT = "current";
}
