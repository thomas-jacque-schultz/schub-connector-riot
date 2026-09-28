package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotKeyMissingException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.business.exceptions.StalePuuidException;
import schultz.thomas.schub.connector.riot.business.quota.QuotaLane;
import schultz.thomas.schub.connector.riot.business.quota.QuotaLaneContext;
import schultz.thomas.schub.connector.riot.business.services.IdSyncResult;
import schultz.thomas.schub.connector.riot.business.services.MatchDetailService;
import schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService;
import schultz.thomas.schub.connector.riot.business.services.ChampionMasteryService;
import schultz.thomas.schub.connector.riot.business.services.MatchHistoryService;
import schultz.thomas.schub.connector.riot.business.services.PuuidValidityService;
import schultz.thomas.schub.connector.riot.business.services.RankingService;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;

import java.util.List;
import java.util.Optional;

// Plusieurs ouvriers (riot.ingest.workers) : la prise de tâche est atomique, chaque tâche n'a qu'un preneur.
@Slf4j
@RequiredArgsConstructor
@Component
public class IngestWorker {

    private final IngestQueue queue;
    private final IngestService ingestService;
    private final MatchHistoryService historyService;
    private final MatchDetailService matchDetailService;
    private final MatchEnrichmentService enrichment;
    private final RankingService rankingService;
    private final ChampionMasteryService masteryService;
    private final PuuidValidityService validity;
    private final RiotProperties properties;
    private final BackgroundCrawler crawler;
    private final LadderSampler sampler;
    private final IngestThroughput throughput;
    private final IngestPause pause;

    public void drain() {
        QuotaLaneContext.runAsBulk(() -> drainTasks(false));
    }

    public void drainPriority() {
        QuotaLaneContext.runAsBulk(() -> drainTasks(true));
    }

    private void drainTasks(boolean reserve) {
        RiotProperties.Ingest config = properties.getIngest();
        if (!config.isEnabled()) {
            return;
        }

        // Relus avant chaque tâche : sous 429, un tour dure des minutes, et une pause doit prendre effet tout de suite.
        for (int handled = 0; handled < config.getBatchSize(); handled++) {
            if (pause.paused()) {
                return;
            }
            Optional<IngestTask> claimed = reserve
                    ? queue.claim(config.getLease(), IngestTask.PRIORITY_FLOOR)
                    : queue.claim(config.getLease(), crawler.active());
            if (claimed.isEmpty() || !run(claimed.get(), config)) {
                return;
            }
        }
    }

    private boolean run(IngestTask task, RiotProperties.Ingest config) {
        try {
            if (task.prioritaire()) {
                QuotaLaneContext.runAs(QuotaLane.PRIORITY, () -> execute(task));
            } else {
                execute(task);
            }
            queue.complete(task);
            throughput.record();
            return true;
        } catch (StalePuuidException perime) {
            validity.markStale(perime.getPuuid());
            queue.complete(task);
            return true;
        } catch (RiotQuotaExceededException saturated) {
            queue.release(task, config.getQuotaBackoff());
            log.info("Quota saturé, ingestion suspendue : {}", saturated.getMessage());
            return false;
        } catch (RiotKeyMissingException asleep) {
            queue.release(task, config.getIdleBackoff());
            log.debug("Connecteur en veille, ingestion repoussée.");
            return false;
        } catch (RuntimeException failure) {
            queue.fail(task, String.valueOf(failure.getMessage()), config.getMaxAttempts(),
                    config.getRetryBackoff());
            return true;
        }
    }

    private void execute(IngestTask task) {
        switch (task.type()) {
            case PLAYER_IDS -> collectIds(task);
            case PLAYER_PREVIEW -> collectPreview(task, false);
            case PLAYER_PREVIEW_SLOW -> collectPreview(task, true);
            case PLAYER_RANK -> rankingService.rankings(task.key());
            case PLAYER_MASTERY -> masteryService.refresh(task.key());
            case PUUID_CHECK -> validity.verify(task.key());
            case MATCH_DETAIL -> collectDetail(task);
            case MATCH_TIMELINE -> enrichment.collectTimeline(task.key());
            case MATCH_TIMELINE_DIGEST -> enrichment.collectDigest(task.key());
            case LADDER_PAGE -> sampler.samplePage(task.key());
            case SEED_MATCHES -> sampler.collectSeed(task.key());
            case MATCH_RANKS -> enrichment.collectRanks(task.key());
        }
    }

    private void collectIds(IngestTask task) {
        releveRang(task.key());
        IdSyncResult relevé = historyService.syncIds(task.key());
        int queued = ingestService.enqueueDetails(task.key(), relevé.seen(), task.background());
        log.info("Historique relevé : {} ids vus, {} nouveaux, {} détails empilés.",
                relevé.seen().size(), relevé.created(), queued);
    }

    // Une page d'identifiants au lieu de tout l'historique : l'aperçu part en quelques appels.
    private void collectPreview(IngestTask task, boolean slow) {
        releveRang(task.key());
        List<String> recentes = historyService.recentIds(task.key(), properties.getIngest().getPreviewMatches());
        int queued = ingestService.enqueuePreviewDetails(task.key(), recentes, slow);
        queue.enqueue(IngestTaskType.PLAYER_IDS, task.key(), task.key(), IngestTask.BACKGROUND_PLAYER_PRIORITY);
        log.info("Aperçu d'un joueur recherché : {} détails empilés{}, historique complet en fond.",
                queued, slow ? " en voie lente" : "");
    }

    // Le rang de chaque compte relevé nourrit le référentiel par ligue ; son échec ne bloque pas l'historique.
    private void releveRang(String puuid) {
        try {
            rankingService.rankings(puuid);
        } catch (RiotQuotaExceededException | RiotKeyMissingException | StalePuuidException bloquant) {
            throw bloquant;
        } catch (RuntimeException failure) {
            log.debug("Rang non relevé pour un compte collecté : {}", failure.getMessage());
        }
    }

    // Riot ne garde qu'environ mille parties par joueur : une partie introuvable est consommée, pas retentée.
    private void collectDetail(IngestTask task) {
        if (matchDetailService.detail(task.key()).isEmpty()) {
            log.warn("Partie introuvable chez Riot, probablement purgée : {}", task.key());
        }
    }
}
