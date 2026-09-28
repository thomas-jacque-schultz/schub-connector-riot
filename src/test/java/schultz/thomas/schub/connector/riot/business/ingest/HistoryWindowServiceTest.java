package schultz.thomas.schub.connector.riot.business.ingest;

import com.mongodb.client.result.DeleteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import schultz.thomas.schub.connector.riot.api.dto.HistoryWindow;
import schultz.thomas.schub.connector.riot.business.exceptions.InvalidHistoryWindowException;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.HistoryWindowSetting;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.model.PlayerHistoryCursor;
import schultz.thomas.schub.connector.riot.data.repository.HistoryWindowSettingRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoryWindowServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-28T12:00:00Z");

    private final HistoryWindowSettingRepository settings = mock(HistoryWindowSettingRepository.class);
    private final IngestQueue queue = mock(IngestQueue.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final RiotProperties properties = new RiotProperties();
    private HistoryWindowService service;

    @BeforeEach
    void setUp() {
        service = new HistoryWindowService(settings, queue, mongo, properties,
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        when(mongo.remove(any(Query.class), eq(IngestTask.class))).thenReturn(DeleteResult.acknowledged(2));
        when(mongo.remove(any(Query.class), eq(PlayerHistoryCursor.class))).thenReturn(DeleteResult.acknowledged(1));
    }

    @Test
    @DisplayName("Sans réglage enregistré, la fenêtre vient de la configuration")
    void valeursParDefaut() {
        when(settings.findById(HistoryWindowSetting.CURRENT)).thenReturn(Optional.empty());

        assertThat(service.current()).isEqualTo(new HistoryWindow(50, 14, 20));
    }

    @Test
    @DisplayName("Au premier démarrage, les détails de fond sont abandonnés et leurs joueurs relevés de nouveau")
    void purgeAuPremierDemarrage() {
        when(settings.findById(HistoryWindowSetting.CURRENT)).thenReturn(Optional.empty());
        when(mongo.findDistinct(any(Query.class), eq("puuid"), eq(IngestTask.class), eq(String.class)))
                .thenReturn(List.of("a", "b"));

        service.pruneIfNeeded();

        verify(mongo).remove(any(Query.class), eq(PlayerHistoryCursor.class));
        verify(queue).enqueue(IngestTaskType.PLAYER_IDS, "a", "a", IngestTask.BACKGROUND_PLAYER_PRIORITY);
        verify(queue).enqueue(IngestTaskType.PLAYER_IDS, "b", "b", IngestTask.BACKGROUND_PLAYER_PRIORITY);
        verify(settings).save(any(HistoryWindowSetting.class));
    }

    @Test
    @DisplayName("Une fenêtre déjà appliquée ne purge rien au redémarrage")
    void pasDeSecondePurge() {
        when(settings.findById(HistoryWindowSetting.CURRENT)).thenReturn(Optional.of(new HistoryWindowSetting(
                HistoryWindowSetting.CURRENT, 50, 14, 20, MAINTENANT.minusSeconds(60), MAINTENANT)));

        service.pruneIfNeeded();

        verify(mongo, never()).remove(any(Query.class), eq(IngestTask.class));
    }

    @Test
    @DisplayName("Une fenêtre hors bornes est refusée")
    void horsBornes() {
        assertThatThrownBy(() -> service.update(new HistoryWindow(0, 14, 0)))
                .isInstanceOf(InvalidHistoryWindowException.class);
        assertThatThrownBy(() -> service.update(new HistoryWindow(50, 14, 60)))
                .isInstanceOf(InvalidHistoryWindowException.class);
        verify(settings, never()).save(any());
    }
}
