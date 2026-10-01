package schultz.thomas.schub.connector.riot.data.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IngestTaskTest {

    private static final Instant RELEVE = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    @DisplayName("les parties des graines passent après les demandes et les pages, avant la collecte de fond")
    void bandesDePriorite() {
        long sequence = IngestTask.sequenceOf("EUW1_7990209944");
        long graine = IngestTask.samplingPriority(sequence);
        long fond = IngestTask.backgroundPriority(RELEVE, sequence);

        assertThat(graine).isLessThan(IngestTask.BACKGROUND_PLAYER_PRIORITY).isGreaterThan(fond).isNegative();
        assertThat(IngestTask.samplingPriority(sequence + 1)).isGreaterThan(graine);
    }

    @Test
    @DisplayName("collecte de fond : un compte relevé plus tôt passe en entier avant le suivant (riot#37)")
    void compteParCompte() {
        Instant plusTard = RELEVE.plus(Duration.ofHours(1));
        long anciennePlusVieille = IngestTask.backgroundPriority(RELEVE, 7_900_000_000L);
        long anciennePlusRecente = IngestTask.backgroundPriority(RELEVE, 7_990_000_000L);
        long suivantePlusRecente = IngestTask.backgroundPriority(plusTard, 7_999_999_999L);

        assertThat(anciennePlusRecente).isGreaterThan(anciennePlusVieille);
        assertThat(anciennePlusVieille).isGreaterThan(suivantePlusRecente);
        assertThat(IngestTask.backgroundPriority(Instant.EPOCH, Long.MAX_VALUE))
                .isLessThan(IngestTask.samplingPriority(0));
        assertThat(IngestTask.backgroundPriority(Instant.MAX, 0)).isEqualTo(Long.MIN_VALUE / 2);
    }
}
