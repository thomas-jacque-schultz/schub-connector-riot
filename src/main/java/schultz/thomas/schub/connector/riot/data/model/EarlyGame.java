package schultz.thomas.schub.connector.riot.data.model;

import java.util.List;

public record EarlyGame(List<Gank> ganks, List<JunglePresence> junglers, List<Objectives> objectives) {

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
}
