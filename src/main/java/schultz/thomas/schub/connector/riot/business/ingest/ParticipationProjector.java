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
import schultz.thomas.schub.connector.riot.api.dto.MatchParticipant;
import schultz.thomas.schub.connector.riot.api.dto.RebuildReport;
import schultz.thomas.schub.connector.riot.business.mapper.RawMatchDecoder;
import schultz.thomas.schub.connector.riot.api.dto.KnownAccountSource;
import schultz.thomas.schub.connector.riot.business.search.KnownAccountIndex;
import schultz.thomas.schub.connector.riot.business.search.SearchName;
import schultz.thomas.schub.connector.riot.business.stats.MetricScaleService;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchParticipationRepository;

import java.time.Clock;
import java.time.Instant;
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

    private final CachedMatchRepository matches;
    private final MatchParticipationRepository participations;
    private final KnownAccountIndex knownAccounts;
    private final RawMatchDecoder decoder;
    private final Clock clock;
    private final MetricScaleService metricScale;
    private final MongoTemplate mongo;

    public int project(MatchDetail detail) {
        Instant now = clock.instant();
        Map<Integer, int[]> equipes = new HashMap<>();
        for (MatchParticipant participant : detail.participants()) {
            int[] totaux = equipes.computeIfAbsent(participant.teamId(), id -> new int[2]);
            totaux[0] += participant.kills();
            totaux[1] += participant.deaths();
        }
        List<MatchParticipation> rows = detail.participants().stream()
                .map(participant -> toParticipation(detail, participant, equipes.get(participant.teamId()), now))
                .toList();
        if (!rows.isEmpty()) {
            participations.saveAll(rows);
            knownAccounts.observeAll(rows.stream().map(ParticipationProjector::toObservation).toList());
        }
        return rows.size();
    }

    public int project(CachedMatch cached) {
        return project(decoder.toDetail(cached.raw()));
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

    private MatchParticipation toParticipation(MatchDetail detail, MatchParticipant participant,
                                               int[] equipe, Instant now) {
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
                MatchParticipation.PROJECTION_VERSION,
                now);
    }

    private String patchOf(String gameVersion) {
        if (gameVersion == null || gameVersion.isBlank()) {
            return null;
        }
        String[] segments = gameVersion.split("\\.");
        return segments.length >= 2 ? segments[0] + "." + segments[1] : gameVersion;
    }
}
