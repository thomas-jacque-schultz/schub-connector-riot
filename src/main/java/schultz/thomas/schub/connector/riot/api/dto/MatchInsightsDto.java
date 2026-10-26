package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import schultz.thomas.schub.connector.riot.data.model.TeamPosition;

import java.time.Instant;
import java.util.List;

@Schema(description = "Ce qu'une partie d'équipe apporte en plus de son détail : rangs relevés et chiffres à 15 minutes.")
public record MatchInsightsDto(
        String matchId,
        boolean timelineAvailable,
        @Schema(description = "Absente : les rangs n'ont pas encore été relevés.") Instant ranksObservedAt,
        @Schema(description = "Absent : pas de timeline, ou postes de la partie inconnus.") EarlyGameDto early,
        @Schema(description = "Objectifs pris sur toute la partie. Absent : pas de timeline.")
        List<EndObjectives> endObjectives,
        @Schema(description = "Les paires de coéquipiers d'un même camp, dix par camp. Absent : pas de timeline.")
        List<Proximity> proximities,
        List<Participant> participants
) {

    public record Participant(
            String puuid,
            int side,
            TeamPosition position,
            int championId,
            RankedStandingDto solo,
            RankedStandingDto flex,
            @Schema(description = "Absent : pas de timeline, ou partie finie avant 15 minutes.") At15 at15,
            @Schema(description = "Absent : pas de timeline.") AtEnd atEnd
    ) {
    }

    @Schema(description = "Or et XP de la dernière image de la timeline, kills de toute la partie.")
    public record AtEnd(int gold, int xp, int kills) {
    }

    public record EndObjectives(int side, int dragons, int heralds) {
    }

    @Schema(description = """
            Deux coéquipiers sur les images de la timeline, une par minute. Une image ne compte pas si l'un des deux
            est à sa fontaine ou mort (temps de réapparition estimé à son niveau). Ensemble : moins de 2 000 unités.""")
    public record Proximity(
            String puuid,
            String matePuuid,
            int side,
            @Schema(description = "Images comptées avant 15 minutes.") int earlyMinutes,
            int earlyTogether,
            @Schema(description = "Images comptées à partir de 15 minutes.") int lateMinutes,
            int lateTogether,
            @Schema(description = "Distance moyenne sur toutes les images comptées. Absente : aucune.")
            Integer meanDistance,
            @Schema(description = "Avant 15 minutes, passages d'ensemble à séparés entre deux images comptées.")
            int earlyDepartures
    ) {
    }

    public record At15(int gold, int xp, int cs, int damageToChampions, int kills, int deaths, int assists) {
    }
}
