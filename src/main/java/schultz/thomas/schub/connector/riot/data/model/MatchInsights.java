package schultz.thomas.schub.connector.riot.data.model;

import java.time.Instant;
import java.util.List;

public record MatchInsights(
        String matchId,
        boolean timelineAvailable,
        Instant ranksObservedAt,
        EarlyGame early,
        List<EndObjectives> endObjectives,
        List<Proximity> proximities,
        List<Isolation> isolations,
        List<Contest> contests,
        List<Participant> participants
) {

    public record Participant(
            String puuid,
            int side,
            TeamPosition position,
            int championId,
            RankedStanding solo,
            RankedStanding flex,
            At15 at15,
            AtEnd atEnd,
            Gold gold
    ) {
    }

    public record AtEnd(int gold, int xp, int kills) {
    }

    // Somme des lignes = earned. Exactes : start, passive, roleQuest, kills, streaksTaken ; le reste est estimé.
    public record Gold(int earned, int start, int passive, int minions, int allyJungle, int enemyJungle,
                       int riverJungle, int supportItem, int roleQuest, int plates, int towers, int inhibitors,
                       int objectives, int kills, int streaksTaken, int assists, int cededBase, int cededStreak,
                       List<Death> deaths) {

        public record Death(int second, int gold, int streak) {
        }
    }

    public record EndObjectives(int side, int dragons, int heralds) {
    }

    public record At15(int gold, int xp, int cs, int damageToChampions, int kills, int deaths, int assists) {
    }
}
