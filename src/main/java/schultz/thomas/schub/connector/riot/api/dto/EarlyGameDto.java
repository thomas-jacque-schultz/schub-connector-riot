package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Les quinze premières minutes lues dans la timeline : ganks, présence des junglers, objectifs.")
public record EarlyGameDto(List<Gank> ganks, List<JunglePresence> junglers, List<Objectives> objectives,
                           @Schema(description = "Absent pour une partie analysée avant la version 7.")
                           List<Proximity> proximity) {

    public enum Lane {
        TOP,
        MID,
        BOT
    }

    public enum Outcome {
        @Schema(description = "Le couloir visé a perdu au moins un joueur, l'attaquant aucun.") KILL,
        @Schema(description = "Des morts des deux côtés.") TRADE,
        @Schema(description = "Aucune mort : le couloir a tenu.") SURVIVED,
        @Schema(description = "L'attaquant a perdu au moins un joueur, le couloir aucun.") COUNTER
    }

    @Schema(description = "Une venue du jungler sur un couloir adverse. Sans kill, elle n'est vue qu'aux "
            + "images de la timeline, une par minute : une venue brève entre deux images échappe.")
    public record Gank(
            int second,
            Lane lane,
            int attackerSide,
            String junglerPuuid,
            @Schema(description = "Les joueurs du couloir visé.") List<String> targetPuuids,
            Outcome outcome,
            int defendersLost,
            int attackersLost,
            @Schema(description = "Les joueurs morts pendant le gank, des deux camps.") List<String> casualtyPuuids,
            @Schema(description = "Objectif du côté du couloir pris par l'attaquant dans les 90 secondes.")
            boolean objectiveFollowUp,
            @Schema(description = "Aucune perte pour l'attaquant, et un mort en face ou un objectif pris.")
            boolean decisive
    ) {
    }

    @Schema(description = "Minutes passées par le jungler de chaque côté de la carte, de 2 à 14 minutes.")
    public record JunglePresence(String puuid, int side, int topMinutes, int midMinutes, int botMinutes) {
    }

    @Schema(description = "Objectifs neutres pris avant 15 minutes.")
    public record Objectives(int side, int dragons, int grubs, int heralds) {
    }

    @Schema(description = "Deux coéquipiers, minute par minute à partir de 2 min. Une minute ne compte pas si l'un des "
            + "deux est à sa fontaine ou mort depuis moins de 30 s ; ensemble = à moins de 2 000 unités.")
    public record Proximity(
            String puuidA,
            String puuidB,
            @Schema(description = "Minutes comptées de 2 à 15 min.") int early,
            int earlyTogether,
            @Schema(description = "Minutes comptées après 15 min.") int late,
            int lateTogether,
            @Schema(description = "Distance moyenne sur toutes les minutes comptées, en unités de la carte.")
            int meanDistance,
            @Schema(description = "Passages d'ensemble à séparés avant 15 min : les roams d'un support.")
            int departures
    ) {
    }
}
