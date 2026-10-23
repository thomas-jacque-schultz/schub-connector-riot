package schultz.thomas.schub.connector.riot.data.model;

import java.util.List;

public record EarlyGame(List<Gank> ganks, List<JunglePresence> junglers, List<Objectives> objectives,
                        Proximity proximity) {

    public enum Lane {
        TOP,
        MID,
        BOT
    }

    public enum Outcome {
        KILL,
        TRADE,
        SURVIVED,
        COUNTER
    }

    public record Gank(
            int second,
            Lane lane,
            int attackerSide,
            String junglerPuuid,
            List<String> targetPuuids,
            Outcome outcome,
            int defendersLost,
            int attackersLost,
            List<String> casualtyPuuids,
            boolean objectiveFollowUp
    ) {

        public boolean decisive() {
            return attackersLost == 0 && (defendersLost > 0 || objectiveFollowUp);
        }
    }

    public record JunglePresence(String puuid, int side, int topMinutes, int midMinutes, int botMinutes) {
    }

    public record Objectives(int side, int dragons, int grubs, int heralds) {
    }

    // Les paires d'un même camp (Schub#151) ; a et b indexent puuids, dans l'ordre de la timeline.
    // early : minutes comptées de 2 à 15 min, late : après ; together : à moins de 2 000 unités.
    // departures : passer d'ensemble à séparés avant 15 min, les roams d'un support.
    public record Proximity(List<String> puuids, List<Pair> pairs) {

        public record Pair(int a, int b, int early, int earlyTogether, int late, int lateTogether,
                           int meanDistance, int departures) {
        }
    }
}
