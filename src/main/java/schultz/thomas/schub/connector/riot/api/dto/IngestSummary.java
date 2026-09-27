package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ce que la collecte a produit, en parties et en profils plutôt qu'en tâches.")
public record IngestSummary(Counts matches, Counts profiles) {

    public record Counts(long retrieved, long analysed, long pending) {
    }
}
