package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.util.List;

@Schema(description = "Échantillon par palier : graines tirées dans les classements sur la fenêtre, contre la cible.")
public record SamplingStatus(Duration window, List<Tier> tiers, long lobbies) {

    public record Tier(String tier, long seeds, int target) {
    }
}
