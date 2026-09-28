package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Moyennes par partie des signaux de timeline d'un joueur, sur ses parties qui ont une timeline.")
public record TimelineHabits(
        @Schema(description = "Parties dont la timeline (complète ou digest) a servi.") int games,
        @Schema(description = "Signal → moyenne sur les parties où il est mesuré.") Map<String, Double> means,
        @Schema(description = "Signal → nombre de parties où il est mesuré (certains exigent une timeline complète).")
        Map<String, Integer> counts
) {
}
