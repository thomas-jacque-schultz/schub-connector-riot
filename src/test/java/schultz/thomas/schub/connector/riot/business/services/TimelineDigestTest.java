package schultz.thomas.schub.connector.riot.business.services;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TimelineDigestTest {

    private static final Document TIMELINE = Document.parse("""
            {"metadata": {"matchId": "EUW1_1", "participants": ["a", "b"]},
             "info": {"frameInterval": 60000, "gameId": 1, "participants": [{"participantId": 1, "puuid": "a"}],
              "frames": [{"timestamp": 900000,
                "participantFrames": {"1": {"participantId": 1, "totalGold": 5000, "xp": 6000, "level": 9,
                  "minionsKilled": 120, "jungleMinionsKilled": 4, "position": {"x": 1, "y": 2},
                  "championStats": {"armor": 80},
                  "damageStats": {"totalDamageDoneToChampions": 3000, "magicDamageDone": 999}}},
                "events": [
                  {"type": "CHAMPION_KILL", "timestamp": 1, "killerId": 1, "victimId": 6,
                   "assistingParticipantIds": [2], "position": {"x": 3, "y": 4},
                   "victimDamageDealt": [{"basic": true}], "victimTeamfightDamageReceived": [{"basic": true}]},
                  {"type": "LEVEL_UP", "timestamp": 2, "participantId": 1},
                  {"type": "ITEM_DESTROYED", "timestamp": 3, "participantId": 1},
                  {"type": "ITEM_PURCHASED", "timestamp": 4, "participantId": 1, "itemId": 1055},
                  {"type": "TURRET_PLATE_DESTROYED", "timestamp": 5, "laneType": "MID_LANE"}]}]}}""");

    @Test
    @DisplayName("un kill garde qui, quand, où et les aides, pas le détail des dégâts reçus")
    void allegeLesKills() {
        Map<String, Object> kill = evenements().getFirst();

        assertThat(kill).containsKeys("killerId", "victimId", "assistingParticipantIds", "position", "timestamp")
                .doesNotContainKeys("victimDamageDealt", "victimTeamfightDamageReceived");
    }

    @Test
    @DisplayName("les événements redondants avec les images tombent, les achats et les plaques restent")
    void filtreLesEvenements() {
        assertThat(evenements()).extracting(event -> event.get("type"))
                .containsExactly("CHAMPION_KILL", "ITEM_PURCHASED", "TURRET_PLATE_DESTROYED");
    }

    @Test
    @DisplayName("l'image par minute garde or, xp, CS, niveau, position et dégâts aux champions")
    void allegeLesImages() {
        Document joueur = image().get("participantFrames", Document.class).get("1", Document.class);

        assertThat(joueur).containsKeys("totalGold", "xp", "level", "minionsKilled", "jungleMinionsKilled", "position")
                .doesNotContainKey("championStats");
        assertThat(joueur.get("damageStats", Document.class))
                .containsEntry("totalDamageDoneToChampions", 3000)
                .doesNotContainKey("magicDamageDone");
    }

    @Test
    @DisplayName("les puuids des participants sont conservés : sans eux, rien ne se relie à la partie")
    void gardeLesParticipants() {
        Document resume = TimelineDigest.of(TIMELINE);

        assertThat(resume.get("metadata", Document.class).getList("participants", String.class))
                .containsExactly("a", "b");
    }

    private static Document image() {
        return TimelineDigest.of(TIMELINE).get("info", Document.class).getList("frames", Document.class).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> evenements() {
        return (List<Map<String, Object>>) image().get("events");
    }
}
