package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.RiotDataInventory;
import schultz.thomas.schub.connector.riot.api.dto.RiotDataPurgeReport;
import schultz.thomas.schub.connector.riot.business.exceptions.IngestNotPausedException;
import schultz.thomas.schub.connector.riot.business.ingest.IngestPause;
import schultz.thomas.schub.connector.riot.data.model.CachedChampionCatalog;
import schultz.thomas.schub.connector.riot.data.model.CachedGameVersion;
import schultz.thomas.schub.connector.riot.data.model.CachedMastery;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.CachedRanking;
import schultz.thomas.schub.connector.riot.data.model.CachedTimeline;
import schultz.thomas.schub.connector.riot.data.model.CachedTimelineDigest;
import schultz.thomas.schub.connector.riot.data.model.CrawlerSetting;
import schultz.thomas.schub.connector.riot.data.model.HistoryWindowSetting;
import schultz.thomas.schub.connector.riot.data.model.IngestPauseSetting;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.KnownAccount;
import schultz.thomas.schub.connector.riot.data.model.LadderBound;
import schultz.thomas.schub.connector.riot.data.model.LadderSeed;
import schultz.thomas.schub.connector.riot.data.model.MatchEarlyStats;
import schultz.thomas.schub.connector.riot.data.model.MatchLobby;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.model.MatchRankSnapshot;
import schultz.thomas.schub.connector.riot.data.model.PlayerHistoryCursor;
import schultz.thomas.schub.connector.riot.data.model.PlayerMatchRef;
import schultz.thomas.schub.connector.riot.data.model.PuuidCheck;
import schultz.thomas.schub.connector.riot.data.model.RankSpan;
import schultz.thomas.schub.connector.riot.data.model.StoredChampionReference;
import schultz.thomas.schub.connector.riot.data.model.StoredReference;
import schultz.thomas.schub.connector.riot.data.model.TeamSide;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Riot chiffre le puuid pour chaque clé : après un changement de clé, tout ce que la collecte a rapporté est à jeter.
// Restent les réglages, et ce qui ne dépend pas de la clé (catalogues Data Dragon, bornes des classements).
@Slf4j
@Service
@RequiredArgsConstructor
public class RiotDataPurge {

    static final List<Class<?>> PURGEES = List.of(CachedMatch.class, CachedTimeline.class,
            CachedTimelineDigest.class, MatchParticipation.class, MatchEarlyStats.class, MatchLobby.class,
            MatchRankSnapshot.class, TeamSide.class, PlayerMatchRef.class, PlayerHistoryCursor.class,
            KnownAccount.class, PuuidCheck.class, CachedRanking.class, RankSpan.class, CachedMastery.class,
            LadderSeed.class, StoredReference.class, StoredChampionReference.class, IngestTask.class);

    static final List<Class<?>> GARDEES = List.of(CrawlerSetting.class, HistoryWindowSetting.class, IngestPauseSetting.class,
            CachedChampionCatalog.class, CachedGameVersion.class, LadderBound.class);

    private final MongoTemplate mongo;
    private final IngestPause pause;
    private final RiotCollections collections;
    private final Clock clock;

    public RiotDataInventory inventory() {
        return new RiotDataInventory(compte(), GARDEES.stream().map(mongo::getCollectionName).toList());
    }

    // Supprimer la collection plutôt que ses documents : instantané quel que soit le volume. Elle renaît en zstd.
    // La file doit être à l'arrêt : un ouvrier en route écrirait dans une collection pas encore réindexée.
    public synchronized RiotDataPurgeReport purge() {
        if (!pause.paused()) {
            throw new IngestNotPausedException("Mets l'ingest en pause avant d'effacer les données Riot.");
        }
        long enRoute = pause.running();
        if (enRoute > 0) {
            throw new IngestNotPausedException(enRoute + " tâche(s) encore en cours : réessaie dans quelques secondes.");
        }
        Instant startedAt = clock.instant();
        Map<String, Long> effaces = compte();
        PURGEES.forEach(collections::recree);
        long total = effaces.values().stream().mapToLong(Long::longValue).sum();
        log.warn("Données Riot effacées : {} documents dans {} collections.", total, effaces.size());
        return new RiotDataPurgeReport(effaces, total, startedAt, clock.instant());
    }

    private Map<String, Long> compte() {
        Map<String, Long> documents = new LinkedHashMap<>();
        PURGEES.forEach(type -> documents.put(mongo.getCollectionName(type), mongo.estimatedCount(type)));
        return documents;
    }
}
