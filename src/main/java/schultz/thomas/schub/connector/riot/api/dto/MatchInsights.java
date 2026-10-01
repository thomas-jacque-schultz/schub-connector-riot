package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Ce qu'une partie d'équipe apporte en plus de son détail : rangs relevés et chiffres à 15 minutes.")
public record MatchInsights(
        String matchId,
        boolean timelineAvailable,
        @Schema(description = "Absente : les rangs n'ont pas encore été relevés.") Instant ranksObservedAt,
        @Schema(description = "Absent : pas de timeline, ou postes de la partie inconnus.") EarlyGame early,
        @Schema(description = "Objectifs pris sur toute la partie. Absent : pas de timeline.")
        List<EndObjectives> endObjectives,
        List<Participant> participants
) {

    public record Participant(
            String puuid,
            int side,
            TeamPosition position,
            int championId,
            RankedStanding solo,
            RankedStanding flex,
            @Schema(description = "Absent : pas de timeline, ou partie finie avant 15 minutes.") At15 at15,
            @Schema(description = "Absent : pas de timeline.") AtEnd atEnd
    ) {
    }

    @Schema(description = "Or et XP de la dernière image de la timeline, kills de toute la partie.")
    public record AtEnd(int gold, int xp, int kills) {
    }

    public record EndObjectives(int side, int dragons, int heralds) {
    }

    public record At15(int gold, int xp, int cs, int damageToChampions, int kills, int deaths, int assists) {
    }
}
