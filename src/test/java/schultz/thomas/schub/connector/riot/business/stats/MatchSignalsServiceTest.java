package schultz.thomas.schub.connector.riot.business.stats;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import schultz.thomas.schub.connector.riot.business.services.ItemCatalogService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MatchSignalsServiceTest {

    private static final int CRIT_A = 3031;
    private static final int CRIT_B = 3036;
    private static final int ARMURE = 3068;
    private static final int RANDUIN = 3143;

    private static List<Document> joueurs() {
        List<Document> l = new ArrayList<>();
        String[] postes = {"TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY"};
        for (int i = 1; i <= 10; i++) {
            Document j = new Document("puuid", "p" + i).append("teamId", i <= 5 ? 100 : 200)
                    .append("teamPosition", postes[(i - 1) % 5])
                    .append("magicDamageTaken", 7000).append("physicalDamageTaken", 3000);
            l.add(j);
        }
        l.get(0).append("item0", ARMURE).append("item1", ARMURE);
        l.get(8).append("item0", CRIT_A).append("item1", CRIT_B);
        l.get(9).append("item0", CRIT_A).append("item1", CRIT_B);
        return l;
    }

    private static ItemCatalogService.ItemCatalog catalogue() {
        return new ItemCatalogService.ItemCatalog("16.19", Set.of(), Set.of(CRIT_A, CRIT_B),
                Map.of(ARMURE, 40.0, RANDUIN, 75.0), Map.of());
    }

    private static Map<String, Map<String, Double>> vide() {
        Map<String, Map<String, Double>> s = new LinkedHashMap<>();
        for (int i = 1; i <= 10; i++) {
            s.put("p" + i, new LinkedHashMap<>());
        }
        return s;
    }

    @Test
    @DisplayName("build : un combattant bien armé face à deux porteurs de critique, sans Randuin ; trop d'armure face au magique")
    void build() {
        Map<String, Map<String, Double>> s = vide();
        MatchSignalsService.build(joueurs(), catalogue(), s);

        assertThat(s.get("p1")).containsEntry("enemyCritCarries", 2.0).containsEntry("frontlineArmored", 1.0)
                .containsEntry("hasAntiCrit", 0.0).containsEntry("armorOverMr", 1.0);
        assertThat(s.get("p1").get("magicDamageShare")).isEqualTo(0.7);
        assertThat(s.get("p6")).containsEntry("enemyCritCarries", 0.0);
    }

    private static Document image(long t, Map<Integer, int[]> positions) {
        Document pf = new Document();
        positions.forEach((pid, p) -> pf.append(String.valueOf(pid),
                new Document("position", new Document("x", p[0]).append("y", p[1]))));
        return new Document("timestamp", t).append("participantFrames", pf).append("events", new ArrayList<Document>());
    }

    @Test
    @DisplayName("comportement : mort isolée sûre juste après une image, combat et premier mort, objectif cédé sans échange")
    void comportement() {
        Map<Integer, int[]> loin = new LinkedHashMap<>();
        for (int pid = 1; pid <= 10; pid++) {
            loin.put(pid, new int[]{pid <= 5 ? 1000 : 13000, pid <= 5 ? 1000 : 13000});
        }
        List<Document> frames = new ArrayList<>();
        for (int m = 0; m <= 20; m++) {
            frames.add(image(m * 60_000L, loin));
        }
        // Le joueur 3 meurt seul 1 s après l'image de la 10e minute, loin de ses alliés restés en base.
        frames.get(10).getList("events", Document.class).add(new Document("type", "CHAMPION_KILL")
                .append("timestamp", 601_000L).append("killerId", 8).append("victimId", 3)
                .append("position", new Document("x", 9000).append("y", 9000)));
        // Un combat : trois morts du camp 100 en 20 s au même endroit, près de leurs alliés.
        for (int k = 0; k < 3; k++) {
            frames.get(15).getList("events", Document.class).add(new Document("type", "CHAMPION_KILL")
                    .append("timestamp", 900_000L + k * 5_000L).append("killerId", 7).append("victimId", 1 + k)
                    .append("position", new Document("x", 1500).append("y", 1500)));
        }
        // Un dragon pris par le camp 200, sans rien en face pour le camp 100.
        frames.get(18).getList("events", Document.class).add(new Document("type", "ELITE_MONSTER_KILL")
                .append("timestamp", 1_080_000L).append("killerTeamId", 200).append("monsterType", "DRAGON")
                .append("position", new Document("x", 9800).append("y", 4400)));

        List<Document> participants = new ArrayList<>();
        for (int pid = 1; pid <= 10; pid++) {
            participants.add(new Document("participantId", pid).append("puuid", "p" + pid));
        }
        Document timeline = new Document("frames", frames).append("participants", participants);
        Map<String, Map<String, Double>> s = vide();

        MatchSignalsService.comportement(joueurs(), timeline, false, s);

        assertThat(s.get("p3")).containsEntry("isolatedDeaths", 1.0);
        assertThat(s.get("p1")).containsEntry("firstDeathsInFights", 1.0).containsEntry("objectivesCededWithoutTrade", 1.0);
        assertThat(s.get("p6")).containsEntry("objectivesCededWithoutTrade", 0.0);
        assertThat(s.get("p1")).doesNotContainKey("gankDeaths");
    }
}
