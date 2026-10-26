package schultz.thomas.schub.connector.riot.business.services;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import schultz.thomas.schub.connector.riot.data.model.Contest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static schultz.thomas.schub.connector.riot.business.services.ProximityAnalyzerTest.PARTICIPANTS;
import static schultz.thomas.schub.connector.riot.business.services.ProximityAnalyzerTest.image;
import static schultz.thomas.schub.connector.riot.business.services.ProximityAnalyzerTest.partie;

class ContestAnalyzerTest {

    // Tout le monde à sa fontaine, donc hors jeu : seuls les joueurs placés par le test comptent.
    private static Map<Integer, int[]> aLaFontaine() {
        Map<Integer, int[]> positions = new HashMap<>();
        IntStream.rangeClosed(1, 10).forEach(i -> positions.put(i, i <= 5 ? new int[] {550, 580} : new int[] {14_300, 14_390}));
        return positions;
    }

    private static Document monstre(long t, String type, int camp, int x, int y) {
        return new Document("type", "ELITE_MONSTER_KILL").append("timestamp", t).append("monsterType", type)
                .append("killerTeamId", camp).append("killerId", camp == 100 ? 2 : 7)
                .append("position", new Document("x", x).append("y", y));
    }

    private static Document kill(long t, int tueur, int victime, List<Integer> aides, int x, int y) {
        return new Document("type", "CHAMPION_KILL").append("timestamp", t).append("killerId", tueur)
                .append("victimId", victime).append("assistingParticipantIds", aides)
                .append("position", new Document("x", x).append("y", y));
    }

    private static List<Contest> analyse(List<Document> images) {
        Document raw = partie(images);
        return ContestAnalyzer.analyse(raw, ProximityAnalyzer.lire(raw, PARTICIPANTS));
    }

    @Test
    @DisplayName("Les larves d'une apparition font un seul objectif, au camp qui en a pris le plus ; présents des deux camps")
    void objectifs() {
        List<Document> images = new ArrayList<>();
        for (int m = 0; m <= 12; m++) {
            Map<Integer, int[]> positions = aLaFontaine();
            List<Document> events = new ArrayList<>();
            if (m == 8) {
                positions.put(2, new int[] {5_000, 9_800});
                positions.put(7, new int[] {4_500, 10_800});
            }
            if (m == 9) {
                positions.put(2, new int[] {5_000, 9_800});
                positions.put(5, new int[] {5_500, 9_500});
                events.add(monstre(8 * 60_000L + 10_000, "HORDE", 100, 4_800, 10_200));
                events.add(monstre(8 * 60_000L + 30_000, "HORDE", 200, 4_800, 10_200));
                events.add(monstre(8 * 60_000L + 50_000, "HORDE", 100, 4_800, 10_200));
            }
            if (m == 11) {
                events.add(monstre(10 * 60_000L + 30_000, "DRAGON", 200, 9_866, 4_414));
            }
            images.add(image(m * 60_000L, positions, events));
        }

        List<Contest> contests = analyse(images);

        assertThat(contests).hasSize(2);
        assertThat(contests.getFirst()).isEqualTo(new Contest(490, "HORDE", 100, 0, 0, contests.getFirst().present()));
        assertThat(contests.getFirst().present()).containsExactlyInAnyOrder("p2", "p5", "p7");
        assertThat(contests.get(1).kind()).isEqualTo("DRAGON");
        assertThat(contests.get(1).side()).isEqualTo(200);
        assertThat(contests.get(1).present()).isEmpty();
    }

    @Test
    @DisplayName("Un combat dans la jungle bleue : kills et morts du camp bleu, impliqués et joueurs à portée ; rien en couloir")
    void combatDansLaJungle() {
        List<Document> images = new ArrayList<>();
        for (int m = 0; m <= 14; m++) {
            Map<Integer, int[]> positions = aLaFontaine();
            List<Document> events = new ArrayList<>();
            if (m == 12) {
                positions.put(5, new int[] {8_000, 6_000});
            }
            if (m == 13) {
                events.add(kill(12 * 60_000L + 10_000, 6, 2, List.of(7), 6_900, 5_400));
                events.add(kill(12 * 60_000L + 20_000, 3, 6, List.of(), 7_000, 5_600));
                events.add(kill(12 * 60_000L + 40_000, 9, 4, List.of(), 13_900, 6_000));
            }
            images.add(image(m * 60_000L, positions, events));
        }

        List<Contest> contests = analyse(images);

        assertThat(contests).hasSize(1);
        Contest combat = contests.getFirst();
        assertThat(combat.kind()).isEqualTo(Contest.JUNGLE_FIGHT);
        assertThat(combat.second()).isEqualTo(730);
        assertThat(combat.side()).isEqualTo(100);
        assertThat(combat.sideKills()).isEqualTo(1);
        assertThat(combat.sideDeaths()).isEqualTo(1);
        assertThat(combat.present()).containsExactlyInAnyOrder("p2", "p3", "p5", "p6", "p7");
    }

    @Test
    @DisplayName("La jungle d'un camp : ni couloir, ni rivière, ni base")
    void jungle() {
        assertThat(ContestAnalyzer.jungle(6_900, 5_400)).isEqualTo(100);
        assertThat(ContestAnalyzer.jungle(11_000, 7_000)).isEqualTo(200);
        assertThat(ContestAnalyzer.jungle(9_866, 4_414)).isNull();
        assertThat(ContestAnalyzer.jungle(13_900, 6_000)).isNull();
        assertThat(ContestAnalyzer.jungle(2_000, 2_000)).isNull();
    }
}
