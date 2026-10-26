package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.ingest.IngestQueue;
import schultz.thomas.schub.connector.riot.business.ingest.ParticipationProjector;
import schultz.thomas.schub.connector.riot.business.ingest.TeamSideProjector;
import schultz.thomas.schub.connector.riot.business.mapper.RawMatchDecoder;
import schultz.thomas.schub.connector.riot.business.model.MatchParticipant;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.CachedTimeline;
import schultz.thomas.schub.connector.riot.data.model.CachedTimelineDigest;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.model.MatchEarlyStats;
import schultz.thomas.schub.connector.riot.data.model.MatchInsights;
import schultz.thomas.schub.connector.riot.data.model.MatchRankSnapshot;
import schultz.thomas.schub.connector.riot.data.model.MatchSeat;
import schultz.thomas.schub.connector.riot.data.model.QueueKind;
import schultz.thomas.schub.connector.riot.data.model.RankedStanding;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.data.repository.CachedTimelineDigestRepository;
import schultz.thomas.schub.connector.riot.data.repository.CachedTimelineRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchEarlyStatsRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchParticipationRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchRankSnapshotRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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

    private final CachedMatchRepository matches;
    private final CachedTimelineRepository timelines;
    private final CachedTimelineDigestRepository digests;
    private final MatchDetailService matchDetailService;
    private final ParticipationProjector projector;
    private final TeamSideProjector teamSides;
    private final MatchRankSnapshotRepository rankSnapshots;
    private final MatchParticipationRepository participations;
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
        Set<String> avecDebut = earlyStats.findByMatchIdIn(voulues).stream()
                .filter(MatchEarlyStats::current)
                .map(MatchEarlyStats::matchId).collect(Collectors.toSet());
        avecTimeline.stream().filter(matchId -> !avecDebut.contains(matchId)).forEach(this::recalculeDebut);
        Set<String> avecRangs = rankSnapshots.findByMatchIdIn(voulues).stream()
                .map(MatchRankSnapshot::matchId).collect(Collectors.toSet());
        int empilees = 0;
        for (String matchId : voulues) {
            long priorite = IngestTask.activePriority(IngestTask.sequenceOf(matchId));
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
                    enregistreDebut(matchId, raw);
                },
                () -> log.warn("Timeline introuvable chez Riot : {}", matchId));
    }

    // La partie d'abord : l'analyse du début de partie lit les postes dans son détail.
    public void collectDigest(String matchId) {
        if (timelines.existsById(matchId) || digests.existsById(matchId)
                || matchDetailService.detail(matchId).isEmpty()) {
            return;
        }
        riotApiClient.timeline(matchId).ifPresentOrElse(
                raw -> {
                    Document resume = TimelineDigest.of(raw);
                    digests.save(new CachedTimelineDigest(matchId, resume, clock.instant()));
                    enregistreDebut(matchId, resume);
                },
                () -> log.warn("Timeline introuvable chez Riot : {}", matchId));
    }

    // Sans appel à Riot : le brut est déjà là.
    private void recalculeDebut(String matchId) {
        timelines.findById(matchId).map(CachedTimeline::raw)
                .or(() -> digests.findById(matchId).map(CachedTimelineDigest::raw))
                .ifPresent(timeline -> enregistreDebut(matchId, timeline));
    }

    private void enregistreDebut(String matchId, Map<String, Object> timeline) {
        earlyStats.save(debut(matchId, timeline));
        projector.reproject(matchId);
        teamSides.project(matchId);
    }

    public int recalculePerimes() {
        List<MatchEarlyStats> perimes = earlyStats.findOutdatedIds(MatchEarlyStats.CURRENT_VERSION);
        perimes.forEach(debut -> recalculeDebut(debut.matchId()));
        return perimes.size();
    }

    private MatchEarlyStats debut(String matchId, Map<String, Object> timeline) {
        List<MatchParticipant> participants = matches.findById(matchId)
                .filter(cached -> cached.raw() != null)
                .map(cached -> decoder.toDetail(cached.raw()).participants())
                .orElse(List.of());
        return new MatchEarlyStats(matchId, TimelineFigures.a15(timeline), EarlyGameAnalyzer.analyse(timeline, participants),
                TimelineFigures.aLaFin(timeline), TimelineFigures.objectifsDeFin(timeline), ProximityAnalyzer.analyse(timeline, participants),
                MatchEarlyStats.CURRENT_VERSION);
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

        // Les participations portent déjà les places : relire le brut (≈ 85 ko par partie) ne servirait qu'à ça.
        Map<String, List<MatchSeat>> places = new LinkedHashMap<>();
        participations.findSeatsByMatchIdIn(voulues).stream()
                .sorted(Comparator.comparingInt(MatchSeat::side)
                        .thenComparing(MatchSeat::position, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(ligne -> places.computeIfAbsent(ligne.matchId(), id -> new ArrayList<>()).add(ligne));

        List<MatchInsights> rendus = new ArrayList<>();
        for (Map.Entry<String, List<MatchSeat>> partie : places.entrySet()) {
            MatchEarlyStats debut = parDebut.get(partie.getKey());
            MatchRankSnapshot rangs = parRangs.get(partie.getKey());
            Map<String, MatchInsights.At15> a15 = debut == null ? Map.of() : debut.byPuuid();
            Map<String, MatchInsights.AtEnd> fin = debut == null || debut.endByPuuid() == null ? Map.of()
                    : debut.endByPuuid();
            rendus.add(new MatchInsights(
                    partie.getKey(),
                    debut != null,
                    rangs == null ? null : rangs.observedAt(),
                    debut == null ? null : debut.game(),
                    debut == null ? null : debut.endObjectives(),
                    debut == null ? null : debut.proximities(),
                    partie.getValue().stream()
                            .map(place -> {
                                List<RankedStanding> siens = rangs == null ? List.of()
                                        : rangs.byPuuid().getOrDefault(place.puuid(), List.of());
                                return new MatchInsights.Participant(place.puuid(), place.side(), place.position(),
                                        place.championId(), file(siens, QueueKind.RANKED_SOLO),
                                        file(siens, QueueKind.RANKED_FLEX), a15.get(place.puuid()),
                                        fin.get(place.puuid()));
                            })
                            .toList()));
        }
        return rendus;
    }

    private static RankedStanding file(List<RankedStanding> standings, QueueKind queue) {
        return standings.stream().filter(standing -> standing.queue() == queue).findFirst().orElse(null);
    }
}
