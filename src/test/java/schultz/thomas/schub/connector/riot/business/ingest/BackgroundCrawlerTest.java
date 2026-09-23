package schultz.thomas.schub.connector.riot.business.ingest;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import schultz.thomas.schub.connector.riot.business.exceptions.CrawlerLockedException;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.CrawlerSetting;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.repository.CrawlerSettingRepository;
import schultz.thomas.schub.connector.riot.data.repository.IngestTaskRepository;
import schultz.thomas.schub.connector.riot.data.repository.KnownAccountRepository;
import schultz.thomas.schub.connector.riot.data.repository.PlayerHistoryCursorRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackgroundCrawlerTest {

    private final RiotProperties properties = new RiotProperties();
    private final CrawlerSettingRepository settings = mock(CrawlerSettingRepository.class);
    private final IngestTaskRepository tasks = mock(IngestTaskRepository.class);
    private final IngestQueue queue = mock(IngestQueue.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final MongoDatabase db = mock(MongoDatabase.class);
    private BackgroundCrawler crawler;

    @BeforeEach
    void setUp() {
        when(mongo.getDb()).thenReturn(db);
        octets(1_000L);
        crawler = new BackgroundCrawler(properties, settings, tasks, mock(KnownAccountRepository.class),
                mock(PlayerHistoryCursorRepository.class), queue, mongo,
                Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC));
    }

    private void octets(long total) {
        when(db.runCommand(any(Document.class))).thenReturn(new Document("totalSize", total));
    }

    @Test
    @DisplayName("En prod, elle tourne toujours et refuse d'être basculée")
    void prodVerrouillee() {
        properties.getCrawler().setSwitchable(false);
        when(settings.findById(CrawlerSetting.CURRENT))
                .thenReturn(Optional.of(new CrawlerSetting(CrawlerSetting.CURRENT, false, null)));

        assertThat(crawler.enabled()).isTrue();
        assertThatThrownBy(() -> crawler.toggle(false)).isInstanceOf(CrawlerLockedException.class);
    }

    @Test
    @DisplayName("En dev, le réglage de l'owner prime sur la valeur par défaut")
    void devBasculable() {
        properties.getCrawler().setSwitchable(true);
        properties.getCrawler().setEnabledByDefault(false);
        when(settings.findById(CrawlerSetting.CURRENT)).thenReturn(Optional.empty());
        assertThat(crawler.enabled()).isFalse();

        when(settings.findById(CrawlerSetting.CURRENT))
                .thenReturn(Optional.of(new CrawlerSetting(CrawlerSetting.CURRENT, true, null)));
        assertThat(crawler.enabled()).isTrue();
    }

    @Test
    @DisplayName("Au-delà du seuil de volume, elle se suspend et le statut le dit")
    void seuilDeVolume() {
        properties.getCrawler().setStorageAlertBytes(500);

        crawler.round();

        verify(queue, never()).enqueue(any(), anyString(), anyString(), anyLong());
        assertThat(crawler.status().storageAlert()).isTrue();
        assertThat(crawler.status().running()).isFalse();
    }

    @Test
    @DisplayName("Tant que son arriéré est plein, elle n'empile rien de plus")
    void arriere() {
        when(tasks.countByState(IngestTaskState.PENDING)).thenReturn(900L);
        when(tasks.countByStateAndPriorityGreaterThanEqual(IngestTaskState.PENDING, 0)).thenReturn(100L);

        crawler.round();

        verify(mongo, never()).getCollection(anyString());
    }
}
