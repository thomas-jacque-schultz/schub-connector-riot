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
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.model.StoredReference;
import schultz.thomas.schub.connector.riot.data.repository.StoredReferenceRepository;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-23T10:00:00Z");

    @Mock private MongoTemplate mongo;
    @Mock private StoredReferenceRepository store;
    @Mock private ParticipationProjector projector;

    private ReferenceService service;

    @BeforeEach
    void setUp() {
        service = new ReferenceService(mongo, store, projector, new RiotProperties(), new TestClock(MAINTENANT));
    }

    @Test
    @DisplayName("les deux derniers patchs se rangent par version : 16.18 passe devant 16.9")
    void derniersPatchsParVersion() {
        when(mongo.findDistinct(any(Query.class), eq("patch"), eq(MatchParticipation.COLLECTION), eq(String.class)))
                .thenReturn(List.of("16.9", "16.18", "16.17", "15.24", "bizarre"));

        assertThat(service.derniersPatchs()).containsExactly("16.18", "16.17");
    }

    @Test
    @DisplayName("les paliers se suivent de Fer à Maître+, chacun commençant où le précédent s'arrête")
    void niveauxCumules() {
        List<ReferenceGrid.Level> niveaux = service.niveaux();

        assertThat(niveaux).extracting(ReferenceGrid.Level::tier).containsExactlyElementsOf(ReferenceService.PALIERS);
        assertThat(niveaux.getFirst().fromPercentile()).isZero();
        assertThat(niveaux.get(3).fromPercentile()).isCloseTo(0.4200, within(1e-3));
        assertThat(niveaux.getLast().fromPercentile()).isCloseTo(0.9921, within(1e-3));
    }

    @Test
    @DisplayName("Maître, GM et Challenger lisent la grille Maître+")
    void sommetRegroupe() {
        assertThat(ReferenceService.groupe("GRANDMASTER")).isEqualTo("MASTER_PLUS");
        assertThat(ReferenceService.groupe("GOLD")).isEqualTo("GOLD");
        assertThat(ReferenceService.groupe(null)).isNull();
    }

    @Test
    @DisplayName("avec un palier demandé, seule sa grille part ; la courbe du ladder et le sens de la métrique restent")
    void filtreParPalier() {
        StoredReference reference = new StoredReference("MEAN/TOP/16.18+16.17", List.of("16.18", "16.17"), "MEAN",
                "TOP", MAINTENANT, "test", List.of(0.0, 1.0), Map.of("deathShare", new StoredReference.Grid(Map.of(
                "GOLD", new StoredReference.TierGrid(40, List.of(0.1, 0.3)),
                "SILVER", new StoredReference.TierGrid(50, List.of(0.1, 0.4))), List.of(0.1, 0.4), List.of())));

        ReferenceGrid.Metric metrique = service.toGrid(reference, "GOLD").metrics().get("deathShare");

        assertThat(metrique.tiers()).containsOnlyKeys("GOLD");
        assertThat(metrique.ladder()).containsExactly(0.1, 0.4);
        assertThat(metrique.polarity()).isEqualTo("LOWER");
    }
}
