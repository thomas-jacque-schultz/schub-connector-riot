package schultz.thomas.schub.connector.riot.business.services;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import schultz.thomas.schub.connector.riot.data.model.MatchInsights.Gold;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoldAnalyzerTest {

    private static Document joueur(int sbires, int monstres) {
        return new Document("totalGold", 0).append("minionsKilled", sbires).append("jungleMinionsKilled", monstres)
                .append("position", new Document("x", 1000).append("y", 1000));
    }

    private static Document image(long timestamp, int sbires1, int monstres1, List<Document> events) {
        return new Document("timestamp", timestamp)
                .append("participantFrames", new Document("1", joueur(sbires1, monstres1)).append("2", joueur(0, 0)))
                .append("events", events);
    }

    private static Document timeline(List<Document> images) {
        return new Document("metadata", new Document("participants", List.of("bleu", "rouge")))
                .append("info", new Document("frames", images));
    }

    private static Document partie(Document bleu, Document rouge) {
        return new Document("info", new Document("participants", List.of(bleu, rouge)));
    }

    private static Document place(String poste, int or, int allie, int adverse, int neutres) {
        return new Document("teamPosition", poste).append("goldEarned", or)
                .append("totalAllyJungleMinionsKilled", allie).append("totalEnemyJungleMinionsKilled", adverse)
                .append("neutralMinionsKilled", neutres);
    }

    private static int somme(Gold or) {
        return or.start() + or.passive() + or.minions() + or.allyJungle() + or.enemyJungle() + or.riverJungle()
                + or.supportItem() + or.roleQuest() + or.plates() + or.towers() + or.inhibitors() + or.objectives()
                + or.kills() + or.streaksTaken() + or.assists();
    }

    @Test
    @DisplayName("Les primes se lisent dans le kill : au tueur ce qu'il prend, à la victime ce qu'elle cède")
    void primes() {
        Document kill = new Document("type", "CHAMPION_KILL").append("timestamp", 90_000).append("killerId", 1)
                .append("victimId", 2).append("bounty", 300).append("shutdownBounty", 150)
                .append("assistingParticipantIds", List.of());
        Map<String, Gold> or = GoldAnalyzer.analyse(
                timeline(List.of(image(0, 0, 0, List.of()), image(60_000, 0, 0, List.of()),
                        image(120_000, 10, 0, List.of(kill)))),
                partie(place("MIDDLE", 1300, 0, 0, 0), place("MIDDLE", 700, 0, 0, 0)));

        Gold bleu = or.get("bleu");
        assertThat(bleu.kills()).isEqualTo(300);
        assertThat(bleu.streaksTaken()).isEqualTo(150);
        assertThat(bleu.start()).isEqualTo(500);
        assertThat(bleu.passive()).isEqualTo(122);
        assertThat(somme(bleu)).isEqualTo(1300);

        Gold rouge = or.get("rouge");
        assertThat(rouge.cededBase()).isEqualTo(300);
        assertThat(rouge.cededStreak()).isEqualTo(150);
        assertThat(rouge.deaths()).containsExactly(new Gold.Death(90, 450, 150));
    }

    @Test
    @DisplayName("L'or des camps se partage entre notre jungle, la leur et la rivière selon les camps comptés par Riot")
    void jungle() {
        Map<String, Gold> or = GoldAnalyzer.analyse(
                timeline(List.of(image(0, 0, 0, List.of()), image(60_000, 0, 20, List.of()),
                        image(120_000, 0, 40, List.of()))),
                partie(place("JUNGLE", 1622, 20, 10, 50), place("TOP", 622, 0, 0, 0)));

        Gold bleu = or.get("bleu");
        assertThat(bleu.allyJungle()).isEqualTo(400);
        assertThat(bleu.enemyJungle()).isEqualTo(200);
        assertThat(bleu.riverJungle()).isEqualTo(400);
        assertThat(somme(bleu)).isEqualTo(1622);
    }

    @Test
    @DisplayName("La quête du tireur finie rapporte son or, et le total retombe toujours sur l'or réel")
    void queteEtTotal() {
        Document quete = new Document("type", "ITEM_DESTROYED").append("timestamp", 100_000)
                .append("participantId", 1).append("itemId", 1202);
        Map<String, Gold> or = GoldAnalyzer.analyse(
                timeline(List.of(image(0, 0, 0, List.of()), image(60_000, 6, 0, List.of()),
                        image(120_000, 14, 0, List.of(quete)))),
                partie(place("BOTTOM", 1500, 0, 0, 0), place("UTILITY", 750, 0, 0, 0)));

        Gold bleu = or.get("bleu");
        assertThat(bleu.roleQuest()).isEqualTo(329);
        assertThat(bleu.minions()).isEqualTo(1500 - 500 - 122 - 329);
        assertThat(somme(bleu)).isEqualTo(1500);
        assertThat(somme(or.get("rouge"))).isEqualTo(750);
        assertThat(or.get("rouge").supportItem()).isPositive();
    }

    @Test
    @DisplayName("Sans la partie, ou avec une seule image, rien n'est calculé")
    void incomplet() {
        assertThat(GoldAnalyzer.analyse(timeline(List.of(image(0, 0, 0, List.of()))),
                partie(place("TOP", 500, 0, 0, 0), place("TOP", 500, 0, 0, 0)))).isEmpty();
        assertThat(GoldAnalyzer.analyse(timeline(List.of(image(0, 0, 0, List.of()), image(60_000, 0, 0, List.of()))),
                new Document())).isEmpty();
    }
}
