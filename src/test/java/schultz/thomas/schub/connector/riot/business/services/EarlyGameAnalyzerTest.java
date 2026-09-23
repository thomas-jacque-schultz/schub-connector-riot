package schultz.thomas.schub.connector.riot.business.services;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import schultz.thomas.schub.connector.riot.api.dto.EarlyGame;
import schultz.thomas.schub.connector.riot.api.dto.EarlyGame.Lane;
import schultz.thomas.schub.connector.riot.api.dto.EarlyGame.Outcome;
import schultz.thomas.schub.connector.riot.api.dto.MatchParticipant;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EarlyGameAnalyzerTest {

    // 1 top bleu, 2 jungler bleu, 3 top rouge, 4 jungler rouge, 5 bot bleu, 6 bot rouge.
    private static final List<String> PUUIDS = List.of("top-b", "jgl-b", "top-r", "jgl-r", "bot-b", "bot-r");
    private static final List<MatchParticipant> PARTICIPANTS = List.of(
            joueur("top-b", TeamPosition.TOP, 100), joueur("jgl-b", TeamPosition.JUNGLE, 100),
            joueur("top-r", TeamPosition.TOP, 200), joueur("jgl-r", TeamPosition.JUNGLE, 200),
            joueur("bot-b", TeamPosition.BOTTOM, 100), joueur("bot-r", TeamPosition.BOTTOM, 200));

    // Positions par défaut : chacun chez soi, loin des autres.
    private static Map<Integer, int[]> repos() {
        return new java.util.HashMap<>(Map.of(
                1, new int[] {1_200, 7_000}, 2, new int[] {3_500, 7_000}, 3, new int[] {6_000, 13_500},
                4, new int[] {11_000, 7_500}, 5, new int[] {9_000, 1_200}, 6, new int[] {13_600, 8_000}));
    }

    private static Document image(long ts, Map<Integer, int[]> positions, List<Document> events) {
        Document frames = new Document();
        positions.forEach((id, p) -> frames.append(String.valueOf(id),
                new Document("position", new Document("x", p[0]).append("y", p[1]))));
        return new Document("timestamp", ts).append("participantFrames", frames).append("events", events);
    }

    private static Document kill(long t, int tueur, int victime, List<Integer> aides, int x, int y) {
        return new Document("type", "CHAMPION_KILL").append("timestamp", t).append("killerId", tueur)
                .append("victimId", victime).append("assistingParticipantIds", aides)
                .append("position", new Document("x", x).append("y", y));
    }

    private static Document dragon(long t, int equipe) {
        return new Document("type", "ELITE_MONSTER_KILL").append("timestamp", t).append("killerTeamId", equipe)
                .append("monsterType", "DRAGON");
    }

    private static Document partie(List<Document> images) {
        return new Document("metadata", new Document("participants", PUUIDS))
                .append("info", new Document("frames", images));
    }

    private static List<Document> minutes(int jusqua) {
        List<Document> images = new ArrayList<>();
        for (int minute = 0; minute <= jusqua; minute++) {
            images.add(image(minute * 60_000L, repos(), new ArrayList<>()));
        }
        return images;
    }

    @Test
    @DisplayName("Kill sur le couloir avec le jungler adverse : un gank décisif, le top n'a pas encaissé")
    void gankReussi() {
        List<Document> images = minutes(15);
        Map<Integer, int[]> contact = repos();
        contact.put(1, new int[] {1_200, 9_000});
        contact.put(4, new int[] {1_500, 9_600});
        images.set(5, image(300_000, contact, List.of(kill(290_000, 3, 1, List.of(4), 1_300, 9_200))));

        EarlyGame early = EarlyGameAnalyzer.analyse(partie(images), PARTICIPANTS);

        assertThat(early.ganks()).singleElement().satisfies(gank -> {
            assertThat(gank.lane()).isEqualTo(Lane.TOP);
            assertThat(gank.attackerSide()).isEqualTo(200);
            assertThat(gank.junglerPuuid()).isEqualTo("jgl-r");
            assertThat(gank.targetPuuids()).containsExactly("top-b");
            assertThat(gank.second()).isEqualTo(290);
            assertThat(gank.outcome()).isEqualTo(Outcome.KILL);
            assertThat(gank.casualtyPuuids()).containsExactly("top-b");
            assertThat(gank.decisive()).isTrue();
        });
    }

    @Test
    @DisplayName("Jungler au contact sans mort : le couloir a tenu ; deux venues espacées font deux ganks")
    void presenceSansKill() {
        List<Document> images = minutes(15);
        for (int minute : List.of(4, 9)) {
            Map<Integer, int[]> contact = repos();
            contact.put(1, new int[] {1_200, 9_000});
            contact.put(4, new int[] {1_800, 9_800});
            images.set(minute, image(minute * 60_000L, contact, List.of()));
        }

        EarlyGame early = EarlyGameAnalyzer.analyse(partie(images), PARTICIPANTS);

        assertThat(early.ganks()).hasSize(2).allSatisfy(gank -> {
            assertThat(gank.outcome()).isEqualTo(Outcome.SURVIVED);
            assertThat(gank.decisive()).isFalse();
        });
    }

    @Test
    @DisplayName("Le jungler meurt sur son gank : contre ; un dragon dans les 90 s rend décisif un gank sans kill")
    void contreEtObjectif() {
        List<Document> images = minutes(15);
        Map<Integer, int[]> contre = repos();
        contre.put(2, new int[] {13_300, 5_200});
        contre.put(6, new int[] {13_600, 5_000});
        images.set(10, image(600_000, contre, List.of(kill(610_000, 6, 2, List.of(), 13_500, 5_100))));
        Map<Integer, int[]> pression = repos();
        pression.put(2, new int[] {13_300, 4_200});
        pression.put(6, new int[] {13_600, 4_000});
        images.set(12, image(720_000, pression, List.of(dragon(760_000, 100))));

        EarlyGame early = EarlyGameAnalyzer.analyse(partie(images), PARTICIPANTS);

        assertThat(early.ganks()).extracting(EarlyGame.Gank::outcome, EarlyGame.Gank::objectiveFollowUp,
                        EarlyGame.Gank::decisive)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Outcome.COUNTER, false, false),
                        org.assertj.core.groups.Tuple.tuple(Outcome.SURVIVED, true, true));
        assertThat(early.objectives()).filteredOn(o -> o.side() == 100).singleElement()
                .extracting(EarlyGame.Objectives::dragons).isEqualTo(1);
    }

    @Test
    @DisplayName("Présence du jungler : une minute par image de 2 à 14, rangée haut, milieu ou bas")
    void presenceDesJunglers() {
        List<Document> images = minutes(15);

        EarlyGame early = EarlyGameAnalyzer.analyse(partie(images), PARTICIPANTS);

        assertThat(early.junglers()).filteredOn(p -> p.puuid().equals("jgl-b")).singleElement()
                .satisfies(p -> assertThat(p.topMinutes()).isEqualTo(13));
        assertThat(early.junglers()).filteredOn(p -> p.puuid().equals("jgl-r")).singleElement()
                .satisfies(p -> assertThat(p.botMinutes()).isEqualTo(13));
    }

    @Test
    @DisplayName("Un poste inconnu : aucun gank ne s'attribue, l'analyse est absente")
    void posteInconnu() {
        List<MatchParticipant> participants = new ArrayList<>(PARTICIPANTS);
        participants.set(0, joueur("top-b", TeamPosition.UNKNOWN, 100));

        assertThat(EarlyGameAnalyzer.analyse(partie(minutes(15)), participants)).isNull();
    }

    @Test
    @DisplayName("« decisive » est calculé, et sérialisé quand même : le cœur le lit dans le JSON")
    void decisiveSerialise() throws Exception {
        EarlyGame.Gank gank = new EarlyGame.Gank(300, Lane.TOP, 200, "jgl-r", List.of("top-b"), Outcome.KILL, 1, 0,
                List.of("top-b"), false);

        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(gank))
                .contains("\"decisive\":true");
    }

    @Test
    @DisplayName("Les couloirs de la carte")
    void zones() {
        assertThat(EarlyGameAnalyzer.zone(1_200, 9_000)).isEqualTo(Lane.TOP);
        assertThat(EarlyGameAnalyzer.zone(6_000, 13_500)).isEqualTo(Lane.TOP);
        assertThat(EarlyGameAnalyzer.zone(7_400, 7_400)).isEqualTo(Lane.MID);
        assertThat(EarlyGameAnalyzer.zone(9_000, 1_200)).isEqualTo(Lane.BOT);
        assertThat(EarlyGameAnalyzer.zone(13_600, 8_000)).isEqualTo(Lane.BOT);
        assertThat(EarlyGameAnalyzer.zone(3_500, 7_000)).isNull();
        assertThat(EarlyGameAnalyzer.zone(1_000, 1_000)).isNull();
    }

    private static MatchParticipant joueur(String puuid, TeamPosition position, int teamId) {
        return new MatchParticipant(puuid, puuid, "EUW", 1, "Champion", position, teamId, false,
                0, 0, 0, 1, 0, 0, 0, 0, 0, 4, 14, List.of(), false);
    }
}
