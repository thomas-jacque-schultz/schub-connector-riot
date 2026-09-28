package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ce que le premier relevé d'un joueur va chercher : ses dernières parties, bornées en nombre et en âge.")
public record HistoryWindow(
        @Schema(description = "Au plus ce nombre de parties.") int maxGames,
        @Schema(description = "Aucune partie plus vieille que ce nombre de jours, sauf pour atteindre le plancher.")
        int maxAgeDays,
        @Schema(description = "Plancher : ce nombre de parties, même au-delà de l'âge maximal.") int minGames
) {

    public static final int MAX_GAMES_LIMIT = 1000;
    public static final int MAX_AGE_DAYS_LIMIT = 730;

    public boolean valid() {
        return maxGames >= 1 && maxGames <= MAX_GAMES_LIMIT
                && maxAgeDays >= 1 && maxAgeDays <= MAX_AGE_DAYS_LIMIT
                && minGames >= 0 && minGames <= maxGames;
    }
}
