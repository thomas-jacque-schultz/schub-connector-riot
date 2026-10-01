package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.IngestEnqueueReport;
import schultz.thomas.schub.connector.riot.api.dto.IngestStatus;
import schultz.thomas.schub.connector.riot.api.dto.PlayerIngestStatus;
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
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
public class IngestService {

    private final IngestQueue queue;
    private final IngestTaskRepository tasks;
    private final CachedMatchRepository matches;
    private final RiotRateLimiter rateLimiter;
    private final IngestThroughput throughput;
    private final RiotProperties properties;
    private final Clock clock;

    public IngestEnqueueReport enqueuePlayer(String puuid) {
        boolean queued = queue.enqueue(IngestTaskType.PLAYER_IDS, puuid, puuid, Long.MAX_VALUE);
        return new IngestEnqueueReport(puuid, queued, status());
    }

    public IngestEnqueueReport enqueuePreview(String puuid, boolean slow) {
        IngestTaskType type = slow ? IngestTaskType.PLAYER_PREVIEW_SLOW : IngestTaskType.PLAYER_PREVIEW;
        boolean queued = queue.enqueue(type, puuid, puuid, IngestTask.SEARCHED_PLAYER_PRIORITY);
        return new IngestEnqueueReport(puuid, queued, status());
    }

    // Les plus récentes d'abord (la séquence croît avec le temps) ; en voie lente, espacées dans le temps.
    public int enqueuePreviewDetails(String puuid, Collection<String> matchIds, boolean slow) {
        List<String> recentes = matchIds.stream()
                .sorted(java.util.Comparator.comparingLong(IngestTask::sequenceOf).reversed())
                .toList();
        int apercu = Math.min(properties.getIngest().getPreviewMatches(), recentes.size());
        Set<String> stockees = matches.findStoredIds(recentes.subList(0, apercu)).stream()
                .map(CachedMatch::matchId)
                .collect(java.util.stream.Collectors.toSet());

        Instant now = clock.instant();
        Duration pas = properties.getIngest().getSlowPreviewSpacing();
        int queued = 0;
        int rang = 0;
        for (String matchId : recentes.subList(0, apercu)) {
            if (stockees.contains(matchId)) {
                continue;
            }
            Instant quand = slow ? now.plus(pas.multipliedBy(rang++)) : now;
            if (queue.enqueue(IngestTaskType.MATCH_DETAIL, matchId, puuid,
                    IngestTask.previewPriority(IngestTask.sequenceOf(matchId)), quand)) {
                queued++;
            }
        }
        return queued + enqueueDetails(puuid, recentes.subList(apercu, recentes.size()), true);
    }

    public int enqueueDetails(String puuid, Collection<String> matchIds, boolean background) {
        Set<String> wanted = new LinkedHashSet<>(matchIds);
        if (wanted.isEmpty()) {
            return 0;
        }

        Set<String> alreadyStored = matches.findStoredIds(wanted).stream()
                .map(CachedMatch::matchId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Instant releve = clock.instant();
        int queued = 0;
        for (String matchId : wanted) {
            if (alreadyStored.contains(matchId)) {
                continue;
            }
            long sequence = IngestTask.sequenceOf(matchId);
            if (queue.enqueue(IngestTaskType.MATCH_DETAIL, matchId, puuid, background
                    ? IngestTask.backgroundPriority(releve, sequence) : IngestTask.activePriority(sequence))) {
                queued++;
            }
        }
        return queued;
    }

    public PlayerIngestStatus statusOf(String puuid) {
        long pending = tasks.countByPuuidAndState(puuid, IngestTaskState.PENDING);
        long running = tasks.countByPuuidAndState(puuid, IngestTaskState.RUNNING);
        long failed = tasks.countByPuuidAndState(puuid, IngestTaskState.FAILED);
        double perMinute = debit();
        long prioritaires = tasks.countByPuuidAndStateInAndPriorityGreaterThanEqual(puuid,
                List.of(IngestTaskState.PENDING, IngestTaskState.RUNNING), IngestTask.previewPriority(0));

        if (pending + running == 0 || perMinute <= 0) {
            return new PlayerIngestStatus(puuid, pending, running, failed, 0, perMinute, null, null, prioritaires);
        }

        long ahead = tasks.findFirstByPuuidAndStateOrderByPriorityAsc(puuid, IngestTaskState.PENDING)
                .map(derniere -> tasks.countByStateAndPriorityGreaterThanEqual(
                        IngestTaskState.PENDING, derniere.priority()))
                .orElse(0L)
                + tasks.countByState(IngestTaskState.RUNNING);

        Duration throttled = rateLimiter.throttledFor();
        Duration remaining = Duration.ofSeconds(Math.round(ahead / perMinute * 60.0)).plus(throttled);
        return new PlayerIngestStatus(puuid, pending, running, failed, ahead, perMinute,
                remaining, clock.instant().plus(remaining), prioritaires);
    }

    public IngestStatus status() {
        long pending = tasks.countByState(IngestTaskState.PENDING);
        long running = tasks.countByState(IngestTaskState.RUNNING);
        long failed = tasks.countByState(IngestTaskState.FAILED);

        double perMinute = debit();
        Duration throttled = rateLimiter.throttledFor();
        long remaining = pending + running;

        Duration drain = perMinute <= 0
                ? Duration.ZERO
                : Duration.ofSeconds(Math.round(remaining / perMinute * 60.0)).plus(throttled);

        return new IngestStatus(pending, running, failed, perMinute, drain,
                clock.instant().plus(drain), throttled);
    }

    // Tâches par minute : mesuré sur les cinq dernières minutes, sinon une tâche par seconde et par ouvrier, sous le quota.
    private double debit() {
        double mesure = throughput.perMinute();
        if (mesure > 0) {
            return mesure;
        }
        return Math.min(rateLimiter.allowedPerMinute(), properties.getIngest().getWorkers() * 60.0);
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
