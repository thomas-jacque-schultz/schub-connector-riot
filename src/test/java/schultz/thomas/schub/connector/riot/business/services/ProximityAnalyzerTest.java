package schultz.thomas.schub.connector.riot.business.services;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import schultz.thomas.schub.connector.riot.business.model.MatchParticipant;
import schultz.thomas.schub.connector.riot.data.model.Proximity;
import schultz.thomas.schub.connector.riot.data.model.TeamPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ProximityAnalyzerTest {

    // 1 à 5 en bleu, 6 à 10 en rouge ; 4 = ADC bleu, 5 = support bleu.
    private static final List<String> PUUIDS = IntStream.rangeClosed(1, 10).mapToObj(i -> "p" + i).toList();
    private static final List<MatchParticipant> PARTICIPANTS = IntStream.rangeClosed(1, 10)
            .mapToObj(i -> joueur("p" + i, i <= 5 ? 100 : 200))
            .toList();

    private static Map<Integer, int[]> eparpilles() {
        Map<Integer, int[]> positions = new HashMap<>();
        IntStream.rangeClosed(1, 10).forEach(i -> positions.put(i, new int[] {3_000 + 1_000 * i, 3_000 + 2_500 * (i % 4)}));
        return positions;
    }

    private static Document image(long ts, Map<Integer, int[]> positions, List<Document> events) {
        Document frames = new Document();
        positions.forEach((id, p) -> frames.append(String.valueOf(id),
                new Document("position", new Document("x", p[0]).append("y", p[1])).append("level", 9)));
        return new Document("timestamp", ts).append("participantFrames", frames).append("events", events);
    }

    private static Document partie(List<Document> images) {
        return new Document("metadata", new Document("participants", PUUIDS))
                .append("info", new Document("frames", images));
    }

    private static Map<Integer, int[]> duoBot(boolean ensemble) {
        Map<Integer, int[]> positions = eparpilles();
        positions.put(4, new int[] {11_000, 1_500});
        positions.put(5, ensemble ? new int[] {11_800, 1_900} : new int[] {7_400, 7_400});
        return positions;
    }

    private static Proximity paire(List<Proximity> paires, String a, String b) {
        return paires.stream().filter(p -> p.puuid().equals(a) && p.matePuuid().equals(b)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Dix paires par camp, jamais entre deux camps")
    void vingtPaires() {
        List<Document> images = new ArrayList<>();
        IntStream.rangeClosed(0, 20).forEach(m -> images.add(image(m * 60_000L, eparpilles(), List.of())));

        List<Proximity> paires = ProximityAnalyzer.analyse(partie(images), PARTICIPANTS);

        assertThat(paires).hasSize(20);
        assertThat(paires).allSatisfy(p -> assertThat(Integer.parseInt(p.puuid().substring(1)) <= 5)
                .isEqualTo(Integer.parseInt(p.matePuuid().substring(1)) <= 5));
        assertThat(paire(paires, "p1", "p2").earlyMinutes()).isEqualTo(14);
        assertThat(paire(paires, "p1", "p2").lateMinutes()).isEqualTo(6);
    }

    @Test
    @DisplayName("Le support quitte son ADC deux fois avant 15 min : deux départs, et les minutes ensemble avant et après")
    void departsDuSupport() {
        List<Document> images = new ArrayList<>();
        images.add(image(0, eparpilles(), List.of()));
        boolean[] ensemble = {true, true, false, true, true, true, false, false, true, true, true, true, true, true,
                true, false, true, true, false, false};
        for (int m = 1; m <= ensemble.length; m++) {
            images.add(image(m * 60_000L, duoBot(ensemble[m - 1]), List.of()));
        }

        Proximity duo = paire(ProximityAnalyzer.analyse(partie(images), PARTICIPANTS), "p4", "p5");

        assertThat(duo.earlyMinutes()).isEqualTo(14);
        assertThat(duo.earlyTogether()).isEqualTo(11);
        assertThat(duo.earlyDepartures()).isEqualTo(2);
        assertThat(duo.lateMinutes()).isEqualTo(6);
        assertThat(duo.lateTogether()).isEqualTo(3);
    }

    @Test
    @DisplayName("Une image où l'un des deux est à sa fontaine, ou vient de mourir, n'est pas comptée")
    void fontaineEtMort() {
        List<Document> images = new ArrayList<>();
        images.add(image(0, eparpilles(), List.of()));
        for (int m = 1; m <= 10; m++) {
            Map<Integer, int[]> positions = duoBot(true);
            if (m == 3) {
                positions.put(5, new int[] {600, 600});
            }
            List<Document> events = m == 6
                    ? List.of(new Document("type", "CHAMPION_KILL").append("timestamp", 6 * 60_000L - 20_000)
                            .append("victimId", 4).append("killerId", 7))
                    : List.of();
            images.add(image(m * 60_000L, positions, events));
        }

        Proximity duo = paire(ProximityAnalyzer.analyse(partie(images), PARTICIPANTS), "p4", "p5");

        assertThat(duo.earlyMinutes()).isEqualTo(8);
        assertThat(duo.earlyTogether()).isEqualTo(8);
        assertThat(duo.earlyDepartures()).isZero();
    }

    @Test
    @DisplayName("Le temps de réapparition s'allonge avec le niveau et la durée de la partie")
    void reapparition() {
        assertThat(ProximityAnalyzer.mort(List.of(100_000L), 125_000L, 1)).isFalse();
        assertThat(ProximityAnalyzer.mort(List.of(100_000L), 108_000L, 1)).isTrue();
        assertThat(ProximityAnalyzer.mort(List.of(40 * 60_000L), 40 * 60_000L + 60_000L, 18)).isTrue();
        assertThat(ProximityAnalyzer.mort(List.of(40 * 60_000L), 40 * 60_000L + 80_000L, 18)).isFalse();
    }

    private static MatchParticipant joueur(String puuid, int camp) {
        return new MatchParticipant(puuid, puuid, "EUW", 1, "Champion", TeamPosition.UNKNOWN, camp, false,
                0, 0, 0, 1, 0, 0, 0, 0, 0, 4, 14, List.of(), false);
    }
}
