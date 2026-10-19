package schultz.thomas.schub.connector.riot.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import schultz.thomas.schub.connector.riot.api.mapper.RiotDtoMapperImpl;
import schultz.thomas.schub.connector.riot.business.ingest.BackgroundCrawler;
import schultz.thomas.schub.connector.riot.business.ingest.HistoryWindowService;
import schultz.thomas.schub.connector.riot.business.ingest.IngestPause;
import schultz.thomas.schub.connector.riot.business.ingest.IngestService;
import schultz.thomas.schub.connector.riot.business.ingest.IngestSummaryService;
import schultz.thomas.schub.connector.riot.business.ingest.LadderSampler;
import schultz.thomas.schub.connector.riot.business.ingest.ParticipationProjector;
import schultz.thomas.schub.connector.riot.business.search.KnownAccountIndex;
import schultz.thomas.schub.connector.riot.business.search.PlayerSearchService;
import schultz.thomas.schub.connector.riot.business.seo.IndexablePlayers;
import schultz.thomas.schub.connector.riot.business.seo.SeoCreatorService;
import schultz.thomas.schub.connector.riot.business.services.ChampionCatalogService;
import schultz.thomas.schub.connector.riot.business.services.ChampionMasteryService;
import schultz.thomas.schub.connector.riot.business.services.MatchDetailService;
import schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService;
import schultz.thomas.schub.connector.riot.business.services.MatchHistoryService;
import schultz.thomas.schub.connector.riot.business.services.PlayerIdentityService;
import schultz.thomas.schub.connector.riot.business.services.PuuidValidityService;
import schultz.thomas.schub.connector.riot.business.services.RankingService;
import schultz.thomas.schub.connector.riot.business.services.RiotDataPurge;
import schultz.thomas.schub.connector.riot.business.stats.ChampionComboService;
import schultz.thomas.schub.connector.riot.business.stats.ChampionComboTable;
import schultz.thomas.schub.connector.riot.business.stats.MatchMetricsService;
import schultz.thomas.schub.connector.riot.business.stats.MetricScaleService;
import schultz.thomas.schub.connector.riot.business.stats.ParticipationStatsService;
import schultz.thomas.schub.connector.riot.business.stats.PatchCalendar;
import schultz.thomas.schub.connector.riot.business.stats.ReferenceService;
import schultz.thomas.schub.connector.riot.business.stats.TimelineHabitService;
import schultz.thomas.schub.connector.riot.config.OpenApiConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Le contrat que lisent les autres services (Schub#124). Changé, il est réécrit ici et le test échoue : on le committe,
// puis on le recopie chez les consommateurs (scripts/contrats.sh du cœur).
@WebMvcTest(properties = "springdoc.writer-with-order-by-keys=true")
@AutoConfigureMockMvc(addFilters = false)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class})
@Import({OpenApiConfiguration.class, RiotDtoMapperImpl.class})
class OpenApiContractTest {

    static final Path CONTRAT = Path.of("openapi/connector-riot.json");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BackgroundCrawler backgroundCrawler;

    @MockBean
    private ChampionCatalogService championCatalogService;

    @MockBean
    private ChampionComboService championComboService;

    @MockBean
    private ChampionComboTable championComboTable;

    @MockBean
    private ChampionMasteryService championMasteryService;

    @MockBean
    private HistoryWindowService historyWindowService;

    @MockBean
    private IndexablePlayers indexablePlayers;

    @MockBean
    private IngestPause ingestPause;

    @MockBean
    private IngestService ingestService;

    @MockBean
    private IngestSummaryService ingestSummaryService;

    @MockBean
    private KnownAccountIndex knownAccountIndex;

    @MockBean
    private LadderSampler ladderSampler;

    @MockBean
    private MatchDetailService matchDetailService;

    @MockBean
    private MatchEnrichmentService matchEnrichmentService;

    @MockBean
    private MatchHistoryService matchHistoryService;

    @MockBean
    private MatchMetricsService matchMetricsService;

    @MockBean
    private MetricScaleService metricScaleService;

    @MockBean
    private ParticipationProjector participationProjector;

    @MockBean
    private ParticipationStatsService participationStatsService;

    @MockBean
    private PatchCalendar patchCalendar;

    @MockBean
    private PlayerIdentityService playerIdentityService;

    @MockBean
    private PlayerSearchService playerSearchService;

    @MockBean
    private PuuidValidityService puuidValidityService;

    @MockBean
    private RankingService rankingService;

    @MockBean
    private ReferenceService referenceService;

    @MockBean
    private RiotDataPurge riotDataPurge;

    @MockBean
    private SeoCreatorService seoCreatorService;

    @MockBean
    private TimelineHabitService timelineHabitService;
    @Test
    @DisplayName("le contrat versionné est celui que le code expose")
    void contratAJour() throws Exception {
        String brut = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        String contrat = new ObjectMapper().readTree(brut).toPrettyString() + "\n";
        String versionne = Files.exists(CONTRAT) ? Files.readString(CONTRAT) : "";
        if (!contrat.equals(versionne)) {
            Files.createDirectories(CONTRAT.getParent());
            Files.writeString(CONTRAT, contrat);
        }
        assertThat(versionne).as("contrat réécrit dans %s : à committer", CONTRAT).isEqualTo(contrat);
    }
}
