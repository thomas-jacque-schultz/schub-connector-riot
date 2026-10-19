package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ce que le premier relevé d'un joueur va chercher : ses dernières parties, bornées en nombre et en âge.")
public record HistoryWindowDto(
        @Schema(description = "Au plus ce nombre de parties.") int maxGames,
        @Schema(description = "Aucune partie plus vieille que ce nombre de jours, sauf pour atteindre le plancher.")
        int maxAgeDays,
        @Schema(description = "Plancher : ce nombre de parties, même au-delà de l'âge maximal.") int minGames
) {
}
