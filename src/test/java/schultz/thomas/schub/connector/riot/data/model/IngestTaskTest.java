package schultz.thomas.schub.connector.riot.data.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IngestTaskTest {

    @Test
    @DisplayName("les parties des graines passent après les demandes et les pages, avant la collecte de fond")
    void bandesDePriorite() {
        long sequence = IngestTask.sequenceOf("EUW1_7990209944");
        long graine = IngestTask.samplingPriority(sequence);
        long fond = IngestTask.backgroundPriority(sequence);

        assertThat(graine).isLessThan(IngestTask.BACKGROUND_PLAYER_PRIORITY).isGreaterThan(fond).isNegative();
        assertThat(IngestTask.samplingPriority(sequence + 1)).isGreaterThan(graine);
    }
}
