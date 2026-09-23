package schultz.thomas.schub.connector.riot.business.services;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import schultz.thomas.schub.connector.riot.api.dto.MatchInsights;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MatchEnrichmentServiceTest {

    private static Document image(long timestamp, int or1, int or2, List<Document> events) {
        return new Document("timestamp", timestamp)
                .append("participantFrames", new Document()
                        .append("1", new Document("totalGold", or1).append("xp", 7000).append("minionsKilled", 110)
                                .append("jungleMinionsKilled", 4)
                                .append("damageStats", new Document("totalDamageDoneToChampions", 5200)))
                        .append("2", new Document("totalGold", or2).append("xp", 6500).append("minionsKilled", 95)
                                .append("jungleMinionsKilled", 0)
                                .append("damageStats", new Document("totalDamageDoneToChampions", 4100))))
                .append("events", events);
    }

    private static Document kill(long timestamp, int killer, int victim, List<Integer> assists) {
        return new Document("type", "CHAMPION_KILL").append("timestamp", timestamp)
                .append("killerId", killer).append("victimId", victim).append("assistingParticipantIds", assists);
    }

    @Test
    @DisplayName("À 15 minutes : l'image de la minute 15, et les éliminations jusqu'à 15:00 seulement")
    void a15() {
        Document raw = new Document("metadata", new Document("participants", List.of("p1", "p2")))
                .append("info", new Document("frames", List.of(
                        image(0, 500, 500, List.of()),
                        image(840_000, 5000, 4800, List.of(kill(700_000, 1, 2, List.of()))),
                        image(900_000, 6100, 5300, List.of(kill(899_000, 2, 1, List.of(1)))),
                        image(960_000, 6700, 5900, List.of(kill(930_000, 1, 2, List.of()))))));

        Map<String, MatchInsights.At15> a15 = MatchEnrichmentService.a15(raw);

        assertThat(a15.get("p1")).isEqualTo(new MatchInsights.At15(6100, 7000, 114, 5200, 1, 1, 1));
        assertThat(a15.get("p2")).isEqualTo(new MatchInsights.At15(5300, 6500, 95, 4100, 1, 1, 0));
    }

    @Test
    @DisplayName("Une partie finie avant 15 minutes n'a pas de chiffres à 15 minutes, et pas des zéros")
    void partieCourte() {
        Document raw = new Document("metadata", new Document("participants", List.of("p1", "p2")))
                .append("info", new Document("frames", List.of(image(0, 500, 500, List.of()))));

        assertThat(MatchEnrichmentService.a15(raw)).isEmpty();
    }
}
