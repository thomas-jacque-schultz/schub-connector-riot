package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Les quinze premières minutes lues dans la timeline : ganks, présence des junglers, objectifs.")
public record EarlyGame(List<Gank> ganks, List<JunglePresence> junglers, List<Objectives> objectives) {

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
            @Schema(description = "Objectif du côté du couloir pris par l'attaquant dans les 90 secondes.")
            boolean objectiveFollowUp
    ) {

        public boolean decisive() {
            return attackersLost == 0 && (defendersLost > 0 || objectiveFollowUp);
        }
    }

    @Schema(description = "Minutes passées par le jungler de chaque côté de la carte, de 2 à 14 minutes.")
    public record JunglePresence(String puuid, int side, int topMinutes, int midMinutes, int botMinutes) {
    }

    @Schema(description = "Objectifs neutres pris avant 15 minutes.")
    public record Objectives(int side, int dragons, int grubs, int heralds) {
    }
}
