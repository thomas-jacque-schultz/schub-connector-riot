package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotKeyMissingException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.business.quota.QuotaLaneContext;
import schultz.thomas.schub.connector.riot.business.services.IdSyncResult;
import schultz.thomas.schub.connector.riot.business.services.MatchDetailService;
import schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService;
import schultz.thomas.schub.connector.riot.business.services.MatchHistoryService;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;

import java.util.Optional;

// Un seul ouvrier : le limiteur est le goulot, paralléliser ne ferait que compliquer le quota.
@Slf4j
@RequiredArgsConstructor
@Component
public class IngestWorker {

    private final IngestQueue queue;
    private final IngestService ingestService;
    private final MatchHistoryService historyService;
    private final MatchDetailService matchDetailService;
    private final MatchEnrichmentService enrichment;
    private final RiotProperties properties;

    public void drain() {
        QuotaLaneContext.runAsBulk(this::drainTasks);
    }

    private void drainTasks() {
        RiotProperties.Ingest config = properties.getIngest();
        if (!config.isEnabled()) {
            return;
        }

        for (int handled = 0; handled < config.getBatchSize(); handled++) {
            Optional<IngestTask> claimed = queue.claim(config.getLease());
            if (claimed.isEmpty() || !run(claimed.get(), config)) {
                return;
            }
        }
    }

    private boolean run(IngestTask task, RiotProperties.Ingest config) {
        try {
            switch (task.type()) {
                case PLAYER_IDS -> collectIds(task);
                case MATCH_DETAIL -> collectDetail(task);
                case MATCH_TIMELINE -> enrichment.collectTimeline(task.key());
                case MATCH_RANKS -> enrichment.collectRanks(task.key());
            }
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

    private void collectIds(IngestTask task) {
        IdSyncResult relevé = historyService.syncIds(task.key());
        int queued = ingestService.enqueueDetails(task.key(), relevé.seen());
        log.info("Historique relevé : {} ids vus, {} nouveaux, {} détails empilés.",
                relevé.seen().size(), relevé.created(), queued);
    }

    // Riot ne garde qu'environ mille parties par joueur : une partie introuvable est consommée, pas retentée.
    private void collectDetail(IngestTask task) {
        if (matchDetailService.detail(task.key()).isEmpty()) {
            log.warn("Partie introuvable chez Riot, probablement purgée : {}", task.key());
        }
    }
}
