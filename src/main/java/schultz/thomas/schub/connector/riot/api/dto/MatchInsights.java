package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Ce qu'une partie d'équipe apporte en plus de son détail : rangs relevés et chiffres à 15 minutes.")
public record MatchInsights(
        String matchId,
        boolean timelineAvailable,
        @Schema(description = "Absente : les rangs n'ont pas encore été relevés.") Instant ranksObservedAt,
        List<Participant> participants
) {

    public record Participant(
            String puuid,
            int side,
            TeamPosition position,
            int championId,
            RankedStanding solo,
            RankedStanding flex,
            @Schema(description = "Absent : pas de timeline, ou partie finie avant 15 minutes.") At15 at15
    ) {
    }

    public record At15(int gold, int xp, int cs, int damageToChampions, int kills, int deaths, int assists) {
    }
}
