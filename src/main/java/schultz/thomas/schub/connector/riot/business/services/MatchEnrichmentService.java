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
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
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
                    earlyStats.save(debut(matchId, raw));
                },
                () -> log.warn("Timeline introuvable chez Riot : {}", matchId));
    }

    // Sans appel à Riot : le brut est déjà là.
    private void recalculeDebut(String matchId) {
        timelines.findById(matchId).ifPresent(timeline -> earlyStats.save(debut(matchId, timeline.raw())));
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
        return new MatchEarlyStats(matchId, a15(timeline, participants), MatchEarlyStats.CURRENT_VERSION);
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
    // Le brut fraîchement reçu porte des Map imbriquées, celui relu de Mongo des Document : on lit des Map.
    static Map<String, MatchInsights.At15> a15(Map<String, Object> raw, List<MatchParticipant> participants) {
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

        Map<String, MatchParticipant> participantParPuuid = new HashMap<>();
        participants.forEach(participant -> participantParPuuid.put(participant.puuid(), participant));
        Map<Integer, MatchParticipant> parId = new HashMap<>();
        for (int index = 0; index < puuids.size(); index++) {
            MatchParticipant participant = participantParPuuid.get(String.valueOf(puuids.get(index)));
            if (participant != null) {
                parId.put(index + 1, participant);
            }
        }
        boolean postesConnus = parId.size() == puuids.size();

        Map<Integer, int[]> kda = new HashMap<>();
        Map<Integer, Integer> ganks = new HashMap<>();
        Map<Integer, Integer> reussis = new HashMap<>();
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
                int victime = (int) nombre(event, "victimId");
                if (postesConnus && jungleAdverseImplique(event, victime, parId)) {
                    ganks.merge(victime, 1, Integer::sum);
                    MatchParticipant cible = parId.get(victime);
                    if (cible.position() != TeamPosition.JUNGLE) {
                        impliques(event).stream().filter(id -> {
                            MatchParticipant acteur = parId.get(id);
                            return acteur != null && acteur.teamId() != cible.teamId();
                        }).distinct().forEach(id -> reussis.merge(id, 1, Integer::sum));
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
                    siens[0], siens[1], siens[2],
                    postesConnus ? ganks.getOrDefault(participantId, 0) : null,
                    postesConnus ? reussis.getOrDefault(participantId, 0) : null));
        }
        return parPuuid;
    }

    private static boolean jungleAdverseImplique(Map<String, Object> event, int victime,
                                                 Map<Integer, MatchParticipant> parId) {
        MatchParticipant cible = parId.get(victime);
        if (cible == null) {
            return false;
        }
        return impliques(event).stream().map(parId::get).anyMatch(acteur -> acteur != null
                && acteur.position() == TeamPosition.JUNGLE && acteur.teamId() != cible.teamId());
    }

    private static List<Integer> impliques(Map<String, Object> event) {
        List<Integer> impliques = new ArrayList<>();
        impliques.add((int) nombre(event, "killerId"));
        for (Object aide : liste(event, "assistingParticipantIds")) {
            if (aide instanceof Number id) {
                impliques.add(id.intValue());
            }
        }
        return impliques;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> enObjet(Object valeur) {
        return valeur instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, Object> objet(Map<String, Object> parent, String champ) {
        return enObjet(parent.get(champ));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> liste(Map<String, Object> parent, String champ) {
        return parent.get(champ) instanceof List<?> liste ? (List<Object>) liste : List.of();
    }

    private static long nombre(Map<String, Object> objet, String champ) {
        return objet.get(champ) instanceof Number n ? n.longValue() : 0L;
    }
}
