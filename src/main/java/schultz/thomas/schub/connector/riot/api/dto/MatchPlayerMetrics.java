package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Les indicateurs d'un joueur sur une seule partie, calculés comme la grille GAME de son poste.")
public record MatchPlayerMetrics(
        String puuid,
        int side,
        TeamPosition position,
        int championId,
        @Schema(description = "Palier tenu à la date de la partie ; absent s'il n'a pas été relevé.") String tier,
        @Schema(description = "Palier de la graine qui a fait collecter la partie, pas celui du joueur.") boolean tierEstimated,
        @Schema(description = "Clé de métrique → valeur ; null quand la partie ne permet pas de la calculer.")
        Map<String, Double> values
) {
}
