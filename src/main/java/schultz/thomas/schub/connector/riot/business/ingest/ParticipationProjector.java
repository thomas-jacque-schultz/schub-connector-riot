package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.MatchDetail;
import schultz.thomas.schub.connector.riot.api.dto.MatchInsights;
import schultz.thomas.schub.connector.riot.api.dto.MatchParticipant;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.api.dto.RebuildReport;
import schultz.thomas.schub.connector.riot.business.mapper.RawMatchDecoder;
import schultz.thomas.schub.connector.riot.api.dto.KnownAccountSource;
import schultz.thomas.schub.connector.riot.business.search.KnownAccountIndex;
import schultz.thomas.schub.connector.riot.business.search.SearchName;
import schultz.thomas.schub.connector.riot.business.services.RankHistory;
import schultz.thomas.schub.connector.riot.business.stats.MetricScaleService;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.MatchEarlyStats;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotMatchResponse;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchEarlyStatsRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchLobbyRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchParticipationRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
public class ParticipationProjector {

    private static final int REBUILD_PAGE = 200;
    static final Duration RANG_TOLERANCE = Duration.ofDays(30);

    private final CachedMatchRepository matches;
    private final MatchParticipationRepository participations;
    private final KnownAccountIndex knownAccounts;
    private final RawMatchDecoder decoder;
    private final Clock clock;
    private final MetricScaleService metricScale;
    private final MongoTemplate mongo;
    private final MatchEarlyStatsRepository earlyStats;
    private final RankHistory rankHistory;
    private final MatchLobbyRepository lobbies;

    public int project(Document raw) {
        RiotMatchResponse response = decoder.decode(raw);
        MatchDetail detail = decoder.toDetail(response);
        Map<Integer, int[]> equipes = new HashMap<>();
        for (MatchParticipant participant : detail.participants()) {
            int[] totaux = equipes.computeIfAbsent(participant.teamId(), id -> new int[3]);
            totaux[0] += participant.kills();
            totaux[1] += participant.deaths();
            totaux[2] += participant.damageToChampions();
        }
        Map<String, RiotMatchResponse.Participant> bruts = new HashMap<>();
        if (response.info().participants() != null) {
            response.info().participants().forEach(brut -> bruts.put(brut.puuid(), brut));
        }
        Contexte contexte = new Contexte(detail, equipes, bruts,
                earlyStats.findById(detail.matchId()).map(MatchEarlyStats::byPuuid).orElse(Map.of()),
                adversaires(detail.participants()), rangs(detail), clock.instant());
        List<MatchParticipation> rows = detail.participants().stream()
                .map(participant -> toParticipation(contexte, participant))
                .toList();
        if (!rows.isEmpty()) {
            participations.saveAll(rows);
            knownAccounts.observeAll(rows.stream().map(ParticipationProjector::toObservation).toList());
        }
        return rows.size();
    }

    public int project(CachedMatch cached) {
        return project(cached.raw());
    }

    // Les chiffres à 15 min arrivent avec la timeline, souvent après la partie : on reprojette alors.
    public void reproject(String matchId) {
        matches.findById(matchId).filter(cached -> cached.raw() != null).ifPresent(this::project);
    }


    public boolean outdated() {
        return participations.existsByProjectionVersionNot(MatchParticipation.PROJECTION_VERSION);
    }

    // Écrase sur place, par plages d'_id : les statistiques restent lisibles pendant toute la reconstruction.
    public RebuildReport rebuildAll() {
        Instant startedAt = clock.instant();
        Bilan bilan = new Bilan();
        String apres = "";
        while (true) {
            List<CachedMatch> lot = matches.findByMatchIdGreaterThanOrderByMatchIdAsc(apres, Limit.of(REBUILD_PAGE));
            lot.forEach(bilan::projette);
            if (lot.size() < REBUILD_PAGE) {
                break;
            }
            apres = lot.getLast().matchId();
        }
        return termine(bilan, startedAt);
    }

    // Seules les lignes d'une version antérieure sont relues : interrompue, elle reprend où elle s'était arrêtée.
    public RebuildReport upgradeOutdated() {
        Instant startedAt = clock.instant();
        Bilan bilan = new Bilan();
        Set<String> sansBrut = new HashSet<>();
        while (true) {
            Query perimees = Query.query(Criteria.where("projectionVersion").ne(MatchParticipation.PROJECTION_VERSION)
                    .and("matchId").nin(sansBrut)).limit(REBUILD_PAGE * 10);
            perimees.fields().include("matchId");
            List<String> ids = mongo.find(perimees, Document.class, MatchParticipation.COLLECTION).stream()
                    .map(ligne -> ligne.getString("matchId"))
                    .distinct()
                    .toList();
            if (ids.isEmpty()) {
                break;
            }
            Map<String, CachedMatch> stockees = new HashMap<>();
            matches.findByMatchIdIn(ids).forEach(cached -> stockees.put(cached.matchId(), cached));
            for (String id : ids) {
                CachedMatch cached = stockees.getOrDefault(id, new CachedMatch(id, null, null));
                if (bilan.projette(cached) == 0) {
                    sansBrut.add(id);
                }
            }
        }
        return termine(bilan, startedAt);
    }

    private RebuildReport termine(Bilan bilan, Instant startedAt) {
        if (bilan.inutilisables > 0) {
            log.warn("{} parties stockées sans JSON brut : antérieures au passage au brut, "
                    + "elles ne produisent aucune participation et doivent être recollectées.", bilan.inutilisables);
        }
        log.info("Couche d'analyse reconstruite : {} parties lues, {} participations écrites.",
                bilan.lues, bilan.ecrites);
        metricScale.refresh();
        return new RebuildReport(bilan.lues, bilan.ecrites, bilan.inutilisables, startedAt, clock.instant());
    }

    private final class Bilan {
        private int lues;
        private int ecrites;
        private int inutilisables;

        private int projette(CachedMatch cached) {
            lues++;
            if (cached.raw() == null) {
                inutilisables++;
                return 0;
            }
            int lignes = project(cached);
            ecrites += lignes;
            return lignes;
        }
    }


    private static KnownAccountIndex.Observation toObservation(MatchParticipation row) {
        return new KnownAccountIndex.Observation(row.puuid(), row.gameName(), row.tagLine(),
                row.startedAt(), KnownAccountSource.PARTICIPATION);
    }

    private MatchParticipation toParticipation(Contexte contexte, MatchParticipant participant) {
        MatchDetail detail = contexte.detail();
        int[] equipe = contexte.equipes().get(participant.teamId());
        return new MatchParticipation(
                MatchParticipation.idOf(participant.puuid(), detail.matchId()),
                participant.puuid(),
                detail.matchId(),
                participant.gameName(),
                participant.tagLine(),
                SearchName.fold(participant.gameName()),
                participant.championId(),
                participant.championName(),
                participant.position(),
                participant.win(),
                participant.teamId(),
                detail.durationSeconds(),
                detail.queueId(),
                detail.queue(),
                detail.gameVersion(),
                patchOf(detail.gameVersion()),
                detail.platform(),
                detail.startedAt(),
                detail.complete(),
                participant.kills(),
                participant.deaths(),
                participant.assists(),
                participant.minionsKilled(),
                participant.goldEarned(),
                participant.damageToChampions(),
                participant.damageTaken(),
                participant.visionScore(),
                equipe[0],
                equipe[1],
                participant.afk(),
                performance(contexte.bruts().get(participant.puuid()), equipe[2]),
                laning(contexte, participant),
                contexte.rangs().get(participant.puuid()),
                MatchParticipation.PROJECTION_VERSION,
                contexte.now());
    }

    private static MatchParticipation.Performance performance(RiotMatchResponse.Participant brut, int degatsEquipe) {
        if (brut == null) {
            return null;
        }
        return new MatchParticipation.Performance(brut.wardsPlaced(), brut.wardsKilled(), brut.detectorWardsPlaced(),
                brut.totalTimeSpentDead(), brut.damageDealtToTurrets(), brut.turretTakedowns(),
                brut.damageDealtToEpicMonsters(), degatsEquipe, plaques(brut));
    }

    private static MatchParticipation.Laning laning(Contexte contexte, MatchParticipant moi) {
        MatchParticipant adversaire = contexte.adversaires().get(moi.puuid());
        MatchInsights.At15 mien = contexte.a15().get(moi.puuid());
        if (adversaire == null && mien == null) {
            return null;
        }
        MatchInsights.At15 sien = adversaire == null ? null : contexte.a15().get(adversaire.puuid());
        boolean face = mien != null && sien != null;
        return new MatchParticipation.Laning(
                adversaire == null ? null : adversaire.puuid(),
                adversaire == null ? null : ecart(plaques(contexte.bruts().get(moi.puuid())),
                        plaques(contexte.bruts().get(adversaire.puuid()))),
                mien == null ? null : mien.gold(),
                mien == null ? null : mien.cs(),
                mien == null ? null : mien.xp(),
                mien == null ? null : mien.kills(),
                mien == null ? null : mien.deaths(),
                face ? mien.gold() - sien.gold() : null,
                face ? mien.cs() - sien.cs() : null,
                face ? mien.xp() - sien.xp() : null,
                face ? mien.kills() - sien.kills() : null);
    }

    private Map<String, MatchParticipation.RankAtGame> rangs(MatchDetail detail) {
        Map<String, MatchParticipation.RankAtGame> rangs = new HashMap<>();
        if (detail.startedAt() == null) {
            return rangs;
        }
        List<String> puuids = detail.participants().stream().map(MatchParticipant::puuid).toList();
        rankHistory.rankAt(puuids, detail.startedAt(), RANG_TOLERANCE).forEach((puuid, span) ->
                rangs.put(puuid, new MatchParticipation.RankAtGame(span.tier(), span.division(), false)));
        lobbies.findById(detail.matchId()).ifPresent(lobby -> puuids.forEach(puuid -> rangs.putIfAbsent(puuid,
                new MatchParticipation.RankAtGame(lobby.tier(), lobby.division(), true))));
        return rangs;
    }

    // Un seul joueur par poste de chaque côté, sinon pas d'adversaire : postes inconnus ou en double.
    static Map<String, MatchParticipant> adversaires(List<MatchParticipant> participants) {
        Map<TeamPosition, List<MatchParticipant>> parPoste = new HashMap<>();
        participants.stream()
                .filter(participant -> participant.position() != null && participant.position() != TeamPosition.UNKNOWN)
                .forEach(participant -> parPoste.computeIfAbsent(participant.position(), poste -> new ArrayList<>())
                        .add(participant));
        Map<String, MatchParticipant> adversaires = new HashMap<>();
        parPoste.values().forEach(face -> {
            if (face.size() == 2 && face.get(0).teamId() != face.get(1).teamId()) {
                adversaires.put(face.get(0).puuid(), face.get(1));
                adversaires.put(face.get(1).puuid(), face.get(0));
            }
        });
        return adversaires;
    }

    // Les plaques tombent à 14:00 : le total de fin de partie est celui de la phase de laning.
    private static Integer plaques(RiotMatchResponse.Participant brut) {
        return brut == null || brut.challenges() == null ? null : brut.challenges().turretPlatesTaken();
    }

    private static Integer ecart(Integer moi, Integer lui) {
        return moi == null || lui == null ? null : moi - lui;
    }

    private record Contexte(MatchDetail detail, Map<Integer, int[]> equipes,
                            Map<String, RiotMatchResponse.Participant> bruts, Map<String, MatchInsights.At15> a15,
                            Map<String, MatchParticipant> adversaires,
                            Map<String, MatchParticipation.RankAtGame> rangs, Instant now) {
    }


    private String patchOf(String gameVersion) {
        if (gameVersion == null || gameVersion.isBlank()) {
            return null;
        }
        String[] segments = gameVersion.split("\\.");
        return segments.length >= 2 ? segments[0] + "." + segments[1] : gameVersion;
    }
}
