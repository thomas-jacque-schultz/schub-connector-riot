package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlayerReferences(
        String puuid,
        TeamPosition position,
        @Schema(description = "Palier retenu : solo, à défaut flex. Absent si le joueur n'est pas classé.") String tier,
        @Schema(description = "Les joueurs collectés du même palier, au même poste.") MetricReference league,
        @Schema(description = "Ses adversaires directs sur la période, au même poste.") MetricReference met
) {
}
