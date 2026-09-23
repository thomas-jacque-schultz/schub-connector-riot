package schultz.thomas.schub.connector.riot.business.services;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import schultz.thomas.schub.connector.riot.api.dto.MatchInsights;
import schultz.thomas.schub.connector.riot.api.dto.MatchParticipant;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;

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

        Map<String, MatchInsights.At15> a15 = MatchEnrichmentService.a15(raw, List.of());

        assertThat(a15.get("p1")).isEqualTo(new MatchInsights.At15(6100, 7000, 114, 5200, 1, 1, 1, null, null));
        assertThat(a15.get("p2")).isEqualTo(new MatchInsights.At15(5300, 6500, 95, 4100, 1, 1, 0, null, null));
    }

    @Test
    @DisplayName("Une partie finie avant 15 minutes n'a pas de chiffres à 15 minutes, et pas des zéros")
    void partieCourte() {
        Document raw = new Document("metadata", new Document("participants", List.of("p1", "p2")))
                .append("info", new Document("frames", List.of(image(0, 500, 500, List.of()))));

        assertThat(MatchEnrichmentService.a15(raw, List.of())).isEmpty();
    }

    @Test
    @DisplayName("Le brut tel que Riot le rend — des Map imbriquées, pas des Document — se lit aussi")
    void brutFraisDeRiot() {
        Map<String, Object> image = Map.of("timestamp", 900_000,
                "participantFrames", Map.of("1", Map.of("totalGold", 6100, "xp", 7000, "minionsKilled", 110,
                        "jungleMinionsKilled", 4, "damageStats", Map.of("totalDamageDoneToChampions", 5200))),
                "events", List.of(Map.of("type", "CHAMPION_KILL", "timestamp", 600_000, "killerId", 1,
                        "victimId", 2, "assistingParticipantIds", List.of())));
        Map<String, Object> raw = Map.of("metadata", Map.of("participants", List.of("p1")),
                "info", Map.of("frames", List.of(image)));

        assertThat(MatchEnrichmentService.a15(raw, List.of()).get("p1"))
                .isEqualTo(new MatchInsights.At15(6100, 7000, 114, 5200, 1, 0, 0, null, null));
    }

    @Test
    @DisplayName("Gank avant 15:00 : subi par le mort, réussi pour ceux qui tuent avec leur jungler")
    void ganksSubis() {
        List<String> puuids = List.of("top-bleu", "jgl-bleu", "top-rouge", "jgl-rouge");
        List<MatchParticipant> participants = List.of(
                joueur("top-bleu", TeamPosition.TOP, 100), joueur("jgl-bleu", TeamPosition.JUNGLE, 100),
                joueur("top-rouge", TeamPosition.TOP, 200), joueur("jgl-rouge", TeamPosition.JUNGLE, 200));
        Document raw = new Document("metadata", new Document("participants", puuids))
                .append("info", new Document("frames", List.of(
                        image(0, 500, 500, List.of(
                                kill(300_000, 4, 1, List.of()),
                                kill(400_000, 3, 1, List.of(4)),
                                kill(500_000, 3, 1, List.of()),
                                kill(600_000, 2, 3, List.of(1)))),
                        quatreJoueurs(image(900_000, 6000, 6000, List.of(kill(910_000, 4, 1, List.of())))))));

        Map<String, MatchInsights.At15> a15 = MatchEnrichmentService.a15(raw, participants);

        assertThat(a15.get("top-bleu").ganksSuffered()).isEqualTo(2);
        assertThat(a15.get("top-rouge").ganksSuffered()).isEqualTo(1);
        assertThat(a15.get("jgl-bleu").ganksSuffered()).isZero();

        assertThat(a15.get("jgl-rouge").ganksSucceeded()).isEqualTo(2);
        assertThat(a15.get("top-rouge").ganksSucceeded()).isEqualTo(1);
        assertThat(a15.get("jgl-bleu").ganksSucceeded()).isEqualTo(1);
        assertThat(a15.get("top-bleu").ganksSucceeded()).isEqualTo(1);
    }

    private static Document quatreJoueurs(Document image) {
        Document frames = image.get("participantFrames", Document.class);
        frames.append("3", frames.get("1")).append("4", frames.get("2"));
        return image;
    }

    private static MatchParticipant joueur(String puuid, TeamPosition position, int teamId) {
        return new MatchParticipant(puuid, puuid, "EUW", 1, "Champion", position, teamId, false,
                0, 0, 0, 1, 0, 0, 0, 0, 0, 4, 14, List.of(), false);
    }
}
