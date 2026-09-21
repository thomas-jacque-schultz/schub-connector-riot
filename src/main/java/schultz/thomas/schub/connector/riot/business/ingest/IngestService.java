package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.IngestEnqueueReport;
import schultz.thomas.schub.connector.riot.api.dto.IngestStatus;
import schultz.thomas.schub.connector.riot.business.quota.RiotRateLimiter;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.data.repository.IngestTaskRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Ce qu'on empile, et ce que la file laisse voir. L'exécution est dans {@link IngestWorker}. */
@Slf4j
@RequiredArgsConstructor
@Service
public class IngestService {

    private final IngestQueue queue;
    private final IngestTaskRepository tasks;
    private final CachedMatchRepository matches;
    private final RiotRateLimiter rateLimiter;
    private final RiotProperties properties;
    private final Clock clock;

    /**
     * Empile le relevé d'historique d'un joueur.
     *
     * <p>Une seule tâche, pas la liste des parties : on ne connaît pas encore les identifiants.
     * C'est l'exécution de cette tâche qui empilera un détail par partie manquante.</p>
     */
    public IngestEnqueueReport enqueuePlayer(String puuid) {
        boolean queued = queue.enqueue(IngestTaskType.PLAYER_IDS, puuid, puuid, Long.MAX_VALUE);
        return new IngestEnqueueReport(puuid, queued, status());
    }

    /**
     * Empile les détails manquants, en écartant d'un seul coup ce qui est déjà stocké.
     *
     * <p>La vérification se fait par {@code _id} sur {@code riot_match}, en une requête pour
     * tout le lot : dédoublonner partie par partie coûterait mille allers-retours là où le
     * premier remplissage en compte déjà mille.</p>
     */
    public int enqueueDetails(String puuid, Collection<String> matchIds) {
        Set<String> wanted = new LinkedHashSet<>(matchIds);
        if (wanted.isEmpty()) {
            return 0;
        }

        Set<String> alreadyStored = matches.findStoredIds(wanted).stream()
                .map(CachedMatch::matchId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        int queued = 0;
        for (String matchId : wanted) {
            if (alreadyStored.contains(matchId)) {
                continue;
            }
            if (queue.enqueue(IngestTaskType.MATCH_DETAIL, matchId, puuid,
                    IngestTask.sequenceOf(matchId))) {
                queued++;
            }
        }
        return queued;
    }

    public IngestStatus status() {
        long pending = tasks.countByState(IngestTaskState.PENDING);
        long running = tasks.countByState(IngestTaskState.RUNNING);
        long failed = tasks.countByState(IngestTaskState.FAILED);

        double perMinute = rateLimiter.allowedPerMinute();
        Duration throttled = rateLimiter.throttledFor();
        long remaining = pending + running;

        Duration drain = perMinute <= 0
                ? Duration.ZERO
                : Duration.ofSeconds(Math.round(remaining / perMinute * 60.0)).plus(throttled);

        return new IngestStatus(pending, running, failed, perMinute, drain,
                clock.instant().plus(drain), throttled);
    }

    public long retryFailed() {
        long rearmed = queue.retryFailed();
        log.info("{} tâches d'ingestion réarmées.", rearmed);
        return rearmed;
    }

    public List<IngestTask> failures() {
        return tasks.findByState(IngestTaskState.FAILED);
    }

}
