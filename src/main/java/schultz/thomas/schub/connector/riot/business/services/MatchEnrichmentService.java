package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.MatchInsights;
import schultz.thomas.schub.connector.riot.api.dto.MatchParticipant;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.ingest.IngestQueue;
import schultz.thomas.schub.connector.riot.business.ingest.ParticipationProjector;
import schultz.thomas.schub.connector.riot.business.ingest.TeamSideProjector;
import schultz.thomas.schub.connector.riot.business.mapper.RawMatchDecoder;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.CachedTimeline;
import schultz.thomas.schub.connector.riot.data.model.CachedTimelineDigest;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.model.MatchEarlyStats;
import schultz.thomas.schub.connector.riot.data.model.MatchRankSnapshot;
import schultz.thomas.schub.connector.riot.data.model.MatchSeat;
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
                    org.bson.Document resume = TimelineDigest.of(raw);
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
        return new MatchEarlyStats(matchId, a15(timeline),
                EarlyGameAnalyzer.analyse(timeline, participants), MatchEarlyStats.CURRENT_VERSION);
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
            rendus.add(new MatchInsights(
                    partie.getKey(),
                    debut != null,
                    rangs == null ? null : rangs.observedAt(),
                    debut == null ? null : debut.game(),
                    partie.getValue().stream()
                            .map(place -> {
                                List<RankedStanding> siens = rangs == null ? List.of()
                                        : rangs.byPuuid().getOrDefault(place.puuid(), List.of());
                                return new MatchInsights.Participant(place.puuid(), place.side(), place.position(),
                                        place.championId(), file(siens, QueueKind.RANKED_SOLO),
                                        file(siens, QueueKind.RANKED_FLEX), a15.get(place.puuid()));
                            })
                            .toList()));
        }
        return rendus;
    }

    private static RankedStanding file(List<RankedStanding> standings, QueueKind queue) {
        return standings.stream().filter(standing -> standing.queue() == queue).findFirst().orElse(null);
    }

    // participantId 1 à 10 dans l'ordre de metadata.participants ; une image par minute.
    // Le brut fraîchement reçu porte des Map imbriquées, celui relu de Mongo des Document : on lit des Map.
    static Map<String, MatchInsights.At15> a15(Map<String, Object> raw) {
        Map<String, Object> metadata = objet(raw, "metadata");
        Map<String, Object> info = objet(raw, "info");
        List<Object> puuids = liste(metadata, "participants");
        List<Object> frames = liste(info, "frames");
        if (puuids.isEmpty() || frames.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> image = frames.stream()
                .map(MatchEnrichmentService::enObjet)
                .filter(frame -> nombre(frame, "timestamp") >= QUINZE_MINUTES_MS)
                .findFirst()
                .orElse(null);
        if (image == null) {
            return Map.of();
        }

        Map<Integer, int[]> kda = new HashMap<>();
        for (Object frame : frames) {
            for (Object brut : liste(enObjet(frame), "events")) {
                Map<String, Object> event = enObjet(brut);
                if (!"CHAMPION_KILL".equals(event.get("type")) || nombre(event, "timestamp") > QUINZE_MINUTES_MS) {
                    continue;
                }
                kda.computeIfAbsent((int) nombre(event, "killerId"), id -> new int[3])[0]++;
                kda.computeIfAbsent((int) nombre(event, "victimId"), id -> new int[3])[1]++;
                for (Object aide : liste(event, "assistingParticipantIds")) {
                    if (aide instanceof Number id) {
                        kda.computeIfAbsent(id.intValue(), cle -> new int[3])[2]++;
                    }
                }
            }
        }

        Map<String, Object> participantFrames = objet(image, "participantFrames");
        Map<String, MatchInsights.At15> parPuuid = new HashMap<>();
        for (int index = 0; index < puuids.size(); index++) {
            int participantId = index + 1;
            Map<String, Object> pf = objet(participantFrames, String.valueOf(participantId));
            if (pf.isEmpty()) {
                continue;
            }
            Map<String, Object> degats = objet(pf, "damageStats");
            int[] siens = kda.getOrDefault(participantId, new int[3]);
            parPuuid.put(String.valueOf(puuids.get(index)), new MatchInsights.At15(
                    (int) nombre(pf, "totalGold"),
                    (int) nombre(pf, "xp"),
                    (int) (nombre(pf, "minionsKilled") + nombre(pf, "jungleMinionsKilled")),
                    (int) nombre(degats, "totalDamageDoneToChampions"),
                    siens[0], siens[1], siens[2]));
        }
        return parPuuid;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> enObjet(Object valeur) {
        return valeur instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    static Map<String, Object> objet(Map<String, Object> parent, String champ) {
        return enObjet(parent.get(champ));
    }

    @SuppressWarnings("unchecked")
    static List<Object> liste(Map<String, Object> parent, String champ) {
        return parent.get(champ) instanceof List<?> liste ? (List<Object>) liste : List.of();
    }

    static long nombre(Map<String, Object> objet, String champ) {
        return objet.get(champ) instanceof Number n ? n.longValue() : 0L;
    }
}
