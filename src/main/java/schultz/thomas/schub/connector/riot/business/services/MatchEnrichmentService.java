package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.MatchDetail;
import schultz.thomas.schub.connector.riot.api.dto.MatchInsights;
import schultz.thomas.schub.connector.riot.api.dto.MatchParticipant;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.ingest.IngestQueue;
import schultz.thomas.schub.connector.riot.business.mapper.RawMatchDecoder;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.CachedTimeline;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.model.MatchEarlyStats;
import schultz.thomas.schub.connector.riot.data.model.MatchRankSnapshot;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.data.repository.CachedTimelineRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchEarlyStatsRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchRankSnapshotRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class MatchEnrichmentService {

    static final long QUINZE_MINUTES_MS = 15 * 60_000L;

    private final CachedMatchRepository matches;
    private final CachedTimelineRepository timelines;
    private final MatchRankSnapshotRepository rankSnapshots;
    private final MatchEarlyStatsRepository earlyStats;
    private final RiotApiClient riotApiClient;
    private final RankingService rankingService;
    private final IngestQueue queue;
    private final RawMatchDecoder decoder;
    private final Clock clock;

    public int enqueueMissing(Collection<String> matchIds) {
        Set<String> voulues = Set.copyOf(matchIds);
        if (voulues.isEmpty()) {
            return 0;
        }
        Set<String> avecTimeline = timelines.findStoredIds(voulues).stream()
                .map(CachedTimeline::matchId).collect(Collectors.toSet());
        Set<String> avecRangs = rankSnapshots.findByMatchIdIn(voulues).stream()
                .map(MatchRankSnapshot::matchId).collect(Collectors.toSet());
        int empilees = 0;
        for (String matchId : voulues) {
            long priorite = IngestTask.sequenceOf(matchId);
            if (!avecTimeline.contains(matchId)
                    && queue.enqueue(IngestTaskType.MATCH_TIMELINE, matchId, null, priorite)) {
                empilees++;
            }
            if (!avecRangs.contains(matchId)
                    && queue.enqueue(IngestTaskType.MATCH_RANKS, matchId, null, priorite)) {
                empilees++;
            }
        }
        if (empilees > 0) {
            log.info("{} tâches d'enrichissement empilées pour {} parties d'équipe", empilees, voulues.size());
        }
        return empilees;
    }

    public void collectTimeline(String matchId) {
        if (timelines.existsById(matchId)) {
            return;
        }
        riotApiClient.timeline(matchId).ifPresentOrElse(
                raw -> {
                    timelines.save(new CachedTimeline(matchId, raw, clock.instant()));
                    earlyStats.save(new MatchEarlyStats(matchId, a15(raw)));
                },
                () -> log.warn("Timeline introuvable chez Riot : {}", matchId));
    }

    public void collectRanks(String matchId) {
        CachedMatch cached = matches.findById(matchId).orElse(null);
        if (cached == null || cached.raw() == null || rankSnapshots.existsById(matchId)) {
            return;
        }
        Map<String, List<RankedStanding>> parPuuid = new LinkedHashMap<>();
        for (MatchParticipant participant : decoder.toDetail(cached.raw()).participants()) {
            if (participant.puuid() != null && !participant.puuid().isBlank()) {
                parPuuid.put(participant.puuid(), rankingService.rankings(participant.puuid()));
            }
        }
        rankSnapshots.save(new MatchRankSnapshot(matchId, parPuuid, clock.instant()));
    }

    public List<MatchInsights> insights(Collection<String> matchIds) {
        Set<String> voulues = Set.copyOf(matchIds);
        Map<String, MatchEarlyStats> parDebut = earlyStats.findByMatchIdIn(voulues).stream()
                .collect(Collectors.toMap(MatchEarlyStats::matchId, Function.identity()));
        Map<String, MatchRankSnapshot> parRangs = rankSnapshots.findByMatchIdIn(voulues).stream()
                .collect(Collectors.toMap(MatchRankSnapshot::matchId, Function.identity()));

        List<MatchInsights> rendus = new ArrayList<>();
        for (CachedMatch cached : matches.findByMatchIdIn(voulues)) {
            if (cached.raw() == null) {
                continue;
            }
            MatchDetail detail = decoder.toDetail(cached.raw());
            MatchEarlyStats debut = parDebut.get(detail.matchId());
            MatchRankSnapshot rangs = parRangs.get(detail.matchId());
            Map<String, MatchInsights.At15> a15 = debut == null ? Map.of() : debut.byPuuid();
            rendus.add(new MatchInsights(
                    detail.matchId(),
                    debut != null,
                    rangs == null ? null : rangs.observedAt(),
                    detail.participants().stream()
                            .map(participant -> {
                                List<RankedStanding> siens = rangs == null ? List.of()
                                        : rangs.byPuuid().getOrDefault(participant.puuid(), List.of());
                                return new MatchInsights.Participant(participant.puuid(),
                                        participant.teamId(), participant.position(),
                                        participant.championId(), file(siens, QueueKind.RANKED_SOLO),
                                        file(siens, QueueKind.RANKED_FLEX), a15.get(participant.puuid()));
                            })
                            .toList()));
        }
        return rendus;
    }

    private static RankedStanding file(List<RankedStanding> standings, QueueKind queue) {
        return standings.stream().filter(standing -> standing.queue() == queue).findFirst().orElse(null);
    }

    // participantId 1 à 10 dans l'ordre de metadata.participants ; une image par minute.
    static Map<String, MatchInsights.At15> a15(Document raw) {
        Document metadata = raw.get("metadata", Document.class);
        Document info = raw.get("info", Document.class);
        if (metadata == null || info == null) {
            return Map.of();
        }
        List<String> puuids = metadata.getList("participants", String.class);
        List<Document> frames = info.getList("frames", Document.class);
        if (puuids == null || frames == null) {
            return Map.of();
        }
        Document image = frames.stream()
                .filter(frame -> nombre(frame, "timestamp") >= QUINZE_MINUTES_MS)
                .findFirst()
                .orElse(null);
        if (image == null) {
            return Map.of();
        }

        Map<Integer, int[]> kda = new HashMap<>();
        for (Document frame : frames) {
            List<Document> events = frame.getList("events", Document.class);
            if (events == null) {
                continue;
            }
            for (Document event : events) {
                if (!"CHAMPION_KILL".equals(event.getString("type")) || nombre(event, "timestamp") > QUINZE_MINUTES_MS) {
                    continue;
                }
                kda.computeIfAbsent((int) nombre(event, "killerId"), id -> new int[3])[0]++;
                kda.computeIfAbsent((int) nombre(event, "victimId"), id -> new int[3])[1]++;
                List<Integer> aides = event.getList("assistingParticipantIds", Integer.class);
                if (aides != null) {
                    aides.forEach(id -> kda.computeIfAbsent(id, cle -> new int[3])[2]++);
                }
            }
        }

        Document participantFrames = image.get("participantFrames", Document.class);
        Map<String, MatchInsights.At15> parPuuid = new HashMap<>();
        for (int index = 0; index < puuids.size(); index++) {
            int participantId = index + 1;
            Document pf = participantFrames == null ? null : participantFrames.get(String.valueOf(participantId), Document.class);
            if (pf == null) {
                continue;
            }
            Document degats = pf.get("damageStats", Document.class);
            int[] siens = kda.getOrDefault(participantId, new int[3]);
            parPuuid.put(puuids.get(index), new MatchInsights.At15(
                    (int) nombre(pf, "totalGold"),
                    (int) nombre(pf, "xp"),
                    (int) (nombre(pf, "minionsKilled") + nombre(pf, "jungleMinionsKilled")),
                    degats == null ? 0 : (int) nombre(degats, "totalDamageDoneToChampions"),
                    siens[0], siens[1], siens[2]));
        }
        return parPuuid;
    }

    private static long nombre(Document document, String champ) {
        Object valeur = document.get(champ);
        return valeur instanceof Number n ? n.longValue() : 0L;
    }
}
