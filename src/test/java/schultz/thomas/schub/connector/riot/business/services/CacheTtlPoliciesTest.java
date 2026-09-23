package schultz.thomas.schub.connector.riot.business.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import schultz.thomas.schub.connector.riot.api.dto.ChampionCatalog;
import schultz.thomas.schub.connector.riot.api.dto.ChampionMastery;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;
import schultz.thomas.schub.connector.riot.business.client.DataDragonClient;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotApiException;
import schultz.thomas.schub.connector.riot.business.mapper.RiotStatsMapper;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.CachedChampionCatalog;
import schultz.thomas.schub.connector.riot.data.model.CachedGameVersion;
import schultz.thomas.schub.connector.riot.data.model.CachedMastery;
import schultz.thomas.schub.connector.riot.data.model.CachedRanking;
import schultz.thomas.schub.connector.riot.data.model.riot.DataDragonChampionList;
import schultz.thomas.schub.connector.riot.data.repository.CachedChampionCatalogRepository;
import schultz.thomas.schub.connector.riot.data.repository.CachedGameVersionRepository;
import schultz.thomas.schub.connector.riot.data.repository.CachedMasteryRepository;
import schultz.thomas.schub.connector.riot.data.repository.CachedRankingRepository;
import schultz.thomas.schub.connector.riot.support.Fixtures;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheTtlPoliciesTest {

    private static final String PUUID = "puuid-de-test";
    private static final Instant MAINTENANT = Instant.parse("2026-09-18T12:00:00Z");

    @Mock private RiotApiClient riotApiClient;
    @Mock private DataDragonClient dataDragonClient;
    @Mock private CachedRankingRepository rankings;
    @Mock private RankHistory history;
    @Mock private CachedMasteryRepository masteries;
    @Mock private CachedChampionCatalogRepository catalogs;
    @Mock private CachedGameVersionRepository gameVersions;

    private RiotProperties properties;
    private TestClock clock;
    private RiotStatsMapper mapper;

    @BeforeEach
    void setUp() {
        properties = new RiotProperties();
        clock = new TestClock(MAINTENANT);
        mapper = new RiotStatsMapper();
    }

    private RankingService rankingService() {
        return new RankingService(riotApiClient, rankings, mapper, properties, history, clock);
    }

    private ChampionCatalogService catalogService() {
        return new ChampionCatalogService(dataDragonClient, catalogs, gameVersions, mapper,
                properties, clock);
    }

    @Test
    @DisplayName("un rang relevé il y a moins d'une heure n'est pas redemandé")
    void leRangTientUneHeure() {
        when(rankings.findById(PUUID)).thenReturn(Optional.of(new CachedRanking(PUUID,
                List.of(standing()), MAINTENANT.minus(Duration.ofMinutes(50)))));

        assertThat(rankingService().rankings(PUUID)).hasSize(1);
        verify(riotApiClient, never()).leagueEntries(anyString());
    }

    @Test
    @DisplayName("passé l'heure, le rang est redemandé : afficher un LP d'hier serait un bug visible")
    void leRangEstRedemandePasseUneHeure() {
        when(rankings.findById(PUUID)).thenReturn(Optional.of(new CachedRanking(PUUID,
                List.of(standing()), MAINTENANT.minus(Duration.ofHours(2)))));
        when(riotApiClient.leagueEntries(PUUID)).thenReturn(List.of());

        assertThat(rankingService().rankings(PUUID)).isEmpty();
        verify(rankings).save(any(CachedRanking.class));
    }

    @Test
    @DisplayName("si Riot ne répond pas, le dernier rang connu est servi plutôt qu'une erreur")
    void leRangPerimeVautMieuxQuUneErreur() {
        when(rankings.findById(PUUID)).thenReturn(Optional.of(new CachedRanking(PUUID,
                List.of(standing()), MAINTENANT.minus(Duration.ofHours(2)))));
        when(riotApiClient.leagueEntries(PUUID)).thenThrow(new RiotApiException("panne"));

        List<RankedStanding> resultat = rankingService().rankings(PUUID);

        assertThat(resultat).hasSize(1);
        assertThat(resultat.get(0).observedAt()).isEqualTo(MAINTENANT.minus(Duration.ofHours(3)));
    }

    @Test
    @DisplayName("les maîtrises tiennent six heures, puis sont redemandées")
    void lesMaitrisesTiennentSixHeures() {
        ChampionMasteryService service = new ChampionMasteryService(riotApiClient, masteries,
                catalogService(), mapper, properties, clock);

        when(masteries.findById(PUUID)).thenReturn(Optional.of(new CachedMastery(PUUID,
                List.of(mastery(126, 468082), mastery(24, 12000)),
                MAINTENANT.minus(Duration.ofHours(5)))));

        assertThat(service.masteries(PUUID, null)).hasSize(2);
        verify(riotApiClient, never()).masteries(anyString());
    }

    @Test
    @DisplayName("la limite est appliquée à la lecture, pas au stockage")
    void laLimiteNeTronquePasLeCache() {
        ChampionMasteryService service = new ChampionMasteryService(riotApiClient, masteries,
                catalogService(), mapper, properties, clock);

        when(masteries.findById(PUUID)).thenReturn(Optional.of(new CachedMastery(PUUID,
                List.of(mastery(126, 468082), mastery(24, 12000), mastery(62, 900)),
                MAINTENANT.minus(Duration.ofHours(1)))));

        assertThat(service.masteries(PUUID, 2)).hasSize(2);
        assertThat(service.masteries(PUUID, null)).hasSize(3);
        assertThat(service.masteries(PUUID, 99)).hasSize(3);
    }

    @Test
    @DisplayName("un catalogue déjà connu pour une version n'est JAMAIS retéléchargé")
    void leCatalogueDUneVersionEstDefinitif() {
        String id = CachedChampionCatalog.idOf("16.18.1", "fr_FR");
        ChampionCatalog connu = new ChampionCatalog("16.18.1", "fr_FR", List.of());
        when(catalogs.findById(id)).thenReturn(Optional.of(
                new CachedChampionCatalog(id, connu, MAINTENANT.minus(Duration.ofDays(200)))));

        assertThat(catalogService().catalog("16.18.1")).isEqualTo(connu);
        verify(dataDragonClient, never()).champions(anyString(), anyString());
    }

    @Test
    @DisplayName("une version inconnue est téléchargée une fois, puis rangée")
    void telechargeUneVersionInconnueUneSeuleFois() {
        when(catalogs.findById(anyString())).thenReturn(Optional.empty());
        when(dataDragonClient.champions("16.18.1", "fr_FR")).thenReturn(
                Fixtures.load("ddragon-champions.json", DataDragonChampionList.class));

        ChampionCatalog catalogue = catalogService().catalog("16.18.1");

        assertThat(catalogue.version()).isEqualTo("16.18.1");
        assertThat(catalogue.champions()).extracting(card -> card.name())
                .containsExactly("Aatrox", "Jax", "Wukong");
        verify(catalogs).save(any(CachedChampionCatalog.class));
    }

    @Test
    @DisplayName("seule la question « quelle est la version courante » porte un TTL")
    void seuleLaVersionCouranteEstPerissable() {
        when(gameVersions.findById(CachedGameVersion.CURRENT)).thenReturn(Optional.of(
                new CachedGameVersion(CachedGameVersion.CURRENT, "16.18.1",
                        MAINTENANT.minus(Duration.ofHours(1)))));

        assertThat(catalogService().currentVersion()).isEqualTo("16.18.1");
        verify(dataDragonClient, never()).versions();
    }

    @Test
    @DisplayName("passé son TTL, la version courante est redemandée")
    void laVersionCouranteEstRedemandee() {
        when(gameVersions.findById(CachedGameVersion.CURRENT)).thenReturn(Optional.of(
                new CachedGameVersion(CachedGameVersion.CURRENT, "16.17.1",
                        MAINTENANT.minus(Duration.ofHours(12)))));
        when(dataDragonClient.versions()).thenReturn(List.of("16.18.1", "16.17.1"));

        assertThat(catalogService().currentVersion()).isEqualTo("16.18.1");
        verify(gameVersions).save(any(CachedGameVersion.class));
    }

    @Test
    @DisplayName("si Data Dragon est muet, la dernière version connue est servie")
    void laVersionPerimeeVautMieuxQuUnPanneauMort() {
        when(gameVersions.findById(CachedGameVersion.CURRENT)).thenReturn(Optional.of(
                new CachedGameVersion(CachedGameVersion.CURRENT, "16.17.1",
                        MAINTENANT.minus(Duration.ofHours(12)))));
        when(dataDragonClient.versions()).thenThrow(new RiotApiException("CDN muet"));

        assertThat(catalogService().currentVersion()).isEqualTo("16.17.1");
    }

    private RankedStanding standing() {
        return new RankedStanding(QueueKind.RANKED_SOLO, "RANKED_SOLO_5x5", "CHALLENGER", "I",
                4659, 1145, 965, false, false, MAINTENANT.minus(Duration.ofHours(3)));
    }

    private ChampionMastery mastery(int championId, int points) {
        return new ChampionMastery(championId, null, 45, points, MAINTENANT, MAINTENANT);
    }
}
