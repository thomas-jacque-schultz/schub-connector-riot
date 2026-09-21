package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotKeyMissingException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.business.services.IdSyncResult;
import schultz.thomas.schub.connector.riot.business.services.MatchDetailService;
import schultz.thomas.schub.connector.riot.business.services.MatchHistoryService;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;

import java.util.Optional;

/**
 * L'ouvrier — <strong>un seul</strong>.
 *
 * <p>Le limiteur est le goulot par construction : paralléliser n'accélérerait rien et ne ferait
 * que compliquer la comptabilité du quota. Le pool du planificateur est fixé à un thread
 * (voir {@code IngestConfiguration}), ce qui rend cette propriété vraie et pas seulement
 * espérée.</p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class IngestWorker {

    private final IngestQueue queue;
    private final IngestService ingestService;
    private final MatchHistoryService historyService;
    private final MatchDetailService matchDetailService;
    private final RiotProperties properties;

    @Scheduled(fixedDelayString = "${riot.ingest.poll-interval:2s}")
    public void drain() {
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

    /** @return {@code false} pour arrêter le tour : ce qui a bloqué bloquera la tâche suivante. */
    private boolean run(IngestTask task, RiotProperties.Ingest config) {
        try {
            switch (task.type()) {
                case PLAYER_IDS -> collectIds(task);
                case MATCH_DETAIL -> collectDetail(task);
            }
            queue.complete(task);
            return true;
        } catch (RiotQuotaExceededException saturated) {
            // Le limiteur a déjà encaissé le Retry-After. La tâche n'est pas fautive : la
            // compter en échec l'écarterait pour une raison qui ne la concerne pas.
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

    /**
     * Une partie introuvable est consommée, pas retentée : Riot ne garde qu'environ mille
     * parties par joueur, et insister sur ce qu'il a purgé ne ferait que brûler du quota.
     */
    private void collectDetail(IngestTask task) {
        if (matchDetailService.detail(task.key()).isEmpty()) {
            log.warn("Partie introuvable chez Riot, probablement purgée : {}", task.key());
        }
    }
}
