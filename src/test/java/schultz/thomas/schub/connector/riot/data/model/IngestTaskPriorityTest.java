package schultz.thomas.schub.connector.riot.data.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IngestTaskPriorityTest {

    private static final long SEQUENCE = 7_997_152_051L;

    @Test
    @DisplayName("Joueurs actifs, puis joueurs recherchés, puis leurs aperçus, puis l'échantillon et la collecte de fond")
    void ordreDesTranches() {
        assertThat(IngestTask.activePriority(0)).isGreaterThan(IngestTask.SEARCHED_PLAYER_PRIORITY);
        assertThat(IngestTask.SEARCHED_PLAYER_PRIORITY).isGreaterThan(IngestTask.previewPriority(SEQUENCE));
        assertThat(IngestTask.previewPriority(0)).isGreaterThan(0);
        assertThat(IngestTask.samplingPriority(SEQUENCE)).isLessThan(IngestTask.BACKGROUND_PLAYER_PRIORITY);
        assertThat(IngestTask.backgroundPriority(java.time.Instant.now(), SEQUENCE)).isLessThan(IngestTask.samplingPriority(0));
        assertThat(IngestTask.activePriority(SEQUENCE)).isLessThan(Long.MAX_VALUE);
    }
}
