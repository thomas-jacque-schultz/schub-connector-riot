package schultz.thomas.schub.connector.riot.business.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import schultz.thomas.schub.connector.riot.api.dto.ReferenceGrid;
import schultz.thomas.schub.connector.riot.business.ingest.ParticipationProjector;
import schultz.thomas.schub.connector.riot.business.services.RankHistory;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.data.model.RankSpan;
import org.bson.Document;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.model.StoredReference;
import schultz.thomas.schub.connector.riot.data.model.StoredChampionReference;
import schultz.thomas.schub.connector.riot.data.repository.StoredChampionReferenceRepository;
import schultz.thomas.schub.connector.riot.data.repository.StoredReferenceRepository;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-23T10:00:00Z");

    @Mock private MongoTemplate mongo;
    @Mock private StoredReferenceRepository store;
    @Mock private StoredChampionReferenceRepository champions;
    @Mock private ParticipationProjector projector;
    @Mock private RankHistory rankHistory;

    private ReferenceService service;

    @BeforeEach
    void setUp() {
        service = new ReferenceService(mongo, store, champions, projector, rankHistory, new TestClock(MAINTENANT));
    }

    @Test
    @DisplayName("les deux derniers patchs se rangent par version : 16.18 passe devant 16.9")
    void derniersPatchsParVersion() {
        when(mongo.findDistinct(any(Query.class), eq("patch"), eq(MatchParticipation.COLLECTION), eq(String.class)))
                .thenReturn(List.of("16.9", "16.18", "16.17", "15.24", "bizarre"));

        assertThat(service.derniersPatchs()).containsExactly("16.18", "16.17");
    }

    @Test
    @DisplayName("seules les parties dont un joueur a désormais un rang sont reprojetées")
    void retamponneSeulementLesRangsApparus() {
        Instant jouee = MAINTENANT.minus(Duration.ofDays(3));
        when(mongo.stream(any(Query.class), eq(Document.class), eq(MatchParticipation.COLLECTION)))
                .thenReturn(java.util.stream.Stream.of(
                        ligne("EUW1_1", "classe", jouee), ligne("EUW1_2", "sans-rang", jouee)));
        when(rankHistory.spansOf(anyCollection())).thenReturn(Map.of("classe", List.of(new RankSpan("s1", "classe",
                QueueKind.RANKED_SOLO, "GOLD", "II", 40, MAINTENANT.minus(Duration.ofDays(1)), MAINTENANT))));

        assertThat(service.retamponne(List.of("16.19"))).isEqualTo(1);
        verify(projector).reproject("EUW1_1");
        verify(projector, never()).reproject("EUW1_2");
    }

    private static Document ligne(String matchId, String puuid, Instant jouee) {
        return new Document("matchId", matchId).append("puuid", puuid).append("startedAt", Date.from(jouee));
    }

    @Test
    @DisplayName("une grille à qui manque un palier, ou absente, rend les référentiels incomplets")
    void incomplet() {
        StoredReference.Grid pleine = new StoredReference.Grid(Map.of(), List.of());
        StoredReference.Grid trouee = new StoredReference.Grid(Map.of(), List.of("IRON"));
        when(store.findFirstByScopeAndPositionOrderByComputedAtDesc(anyString(), anyString()))
                .thenReturn(Optional.of(reference(pleine)));
        assertThat(service.incomplete()).isFalse();

        when(store.findFirstByScopeAndPositionOrderByComputedAtDesc("GAME", "UTILITY"))
                .thenReturn(Optional.of(reference(trouee)));
        assertThat(service.incomplete()).isTrue();

        when(store.findFirstByScopeAndPositionOrderByComputedAtDesc("GAME", "UTILITY")).thenReturn(Optional.empty());
        assertThat(service.incomplete()).isTrue();
    }

    private static StoredReference reference(StoredReference.Grid grille) {
        return new StoredReference("id", List.of("16.19"), "GAME", "TOP", MAINTENANT, List.of(0.0, 1.0),
                Map.of("csPerMinute", grille));
    }

    @Test
    @DisplayName("Maître, GM et Challenger lisent la grille Maître+")
    void sommetRegroupe() {
        assertThat(ReferenceService.groupe("GRANDMASTER")).isEqualTo("MASTER_PLUS");
        assertThat(ReferenceService.groupe("GOLD")).isEqualTo("GOLD");
        assertThat(ReferenceService.groupe(null)).isNull();
    }

    @Test
    @DisplayName("avec un palier demandé, seule sa grille part ; les médianes par partie de tous les paliers restent")
    void filtreParPalier() {
        StoredReference moyennes = new StoredReference("MEAN/TOP/16.18+16.17", List.of("16.18", "16.17"), "MEAN",
                "TOP", MAINTENANT, List.of(0.0, 1.0), Map.of("deathShare", new StoredReference.Grid(Map.of(
                "GOLD", new StoredReference.TierGrid(40, List.of(0.1, 0.3)),
                "SILVER", new StoredReference.TierGrid(50, List.of(0.1, 0.4))), List.of())));
        StoredReference parPartie = new StoredReference("GAME/TOP/16.18+16.17", List.of("16.18", "16.17"), "GAME",
                "TOP", MAINTENANT, List.of(0.0, 0.25, 0.5, 0.75, 1.0), Map.of("deathShare", new StoredReference.Grid(
                Map.of("SILVER", quartiles(0.30), "GOLD", quartiles(0.25), "PLATINUM", quartiles(0.20)), List.of())));
        when(store.findByScopeAndPositionAndPatchesContainingOrderByComputedAtDesc("GAME", "TOP", "16.18"))
                .thenReturn(List.of(parPartie));

        ReferenceGrid.Metric metrique = service.toGrid(moyennes, "GOLD").metrics().get("deathShare");

        assertThat(metrique.tiers()).containsOnlyKeys("GOLD");
        assertThat(metrique.rankMedians()).containsExactly(Map.entry("SILVER", 0.30), Map.entry("GOLD", 0.25),
                Map.entry("PLATINUM", 0.20));
        assertThat(metrique.polarity()).isEqualTo("LOWER");
    }

    private static StoredReference.TierGrid quartiles(double mediane) {
        return new StoredReference.TierGrid(ReferenceService.MINIMUM_PARTIES,
                List.of(mediane - 0.1, mediane - 0.05, mediane, mediane + 0.05, mediane + 0.1));
    }

    @Test
    @DisplayName("un champion se compare par groupe de paliers : Or et Argent ensemble, le sommet à part")
    void groupesDeChampion() {
        assertThat(ReferenceService.groupeChampion("GOLD")).isEqualTo("SILVER_GOLD");
        assertThat(ReferenceService.groupeChampion("EMERALD")).isEqualTo("PLATINUM_EMERALD");
        assertThat(ReferenceService.groupeChampion("CHALLENGER")).isEqualTo("MASTER_PLUS");
        assertThat(ReferenceService.groupeChampion(null)).isNull();
    }

    @Test
    @DisplayName("la grille d'un champion ne rend que le groupe du joueur ; une métrique sans ce groupe disparaît")
    void grilleDeChampion() {
        when(champions.findFirstByChampionIdOrderByComputedAtDesc(222)).thenReturn(Optional.of(new StoredChampionReference(
                "222/16.18", 222, List.of("16.18"), MAINTENANT, List.of(0.0, 1.0), Map.of(
                "csPerMinute", Map.of("SILVER_GOLD", new StoredReference.TierGrid(40, List.of(6.0, 9.0))),
                "kda", Map.of("DIAMOND", new StoredReference.TierGrid(35, List.of(1.0, 5.0)))))));

        var grille = service.championGrid(222, "GOLD").orElseThrow();

        assertThat(grille.group()).isEqualTo("SILVER_GOLD");
        assertThat(grille.metrics()).containsOnlyKeys("csPerMinute");
        assertThat(grille.metrics().get("csPerMinute").count()).isEqualTo(40);
    }
}
