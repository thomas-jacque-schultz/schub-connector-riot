package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
import java.util.List;
import java.util.Map;

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

    public RebuildReport rebuildAll() {
        Instant startedAt = clock.instant();
        participations.deleteAll();

        int matchesRead = 0;
        int rowsWritten = 0;
        int unusable = 0;
        Pageable page = PageRequest.of(0, REBUILD_PAGE);

        while (true) {
            Slice<CachedMatch> slice = matches.findAll(page);
            for (CachedMatch cached : slice.getContent()) {
                matchesRead++;
                if (cached.raw() == null) {
                    unusable++;
                    continue;
                }
                rowsWritten += project(cached);
            }
            if (!slice.hasNext()) {
                break;
            }
            page = slice.nextPageable();
        }

        if (unusable > 0) {
            log.warn("{} parties stockées sans JSON brut : antérieures au passage au brut, "
                    + "elles ne produisent aucune participation et doivent être recollectées.", unusable);
        }
        log.info("Couche d'analyse reconstruite : {} parties lues, {} participations écrites.",
                matchesRead, rowsWritten);
        metricScale.refresh();
        return new RebuildReport(matchesRead, rowsWritten, unusable, startedAt, clock.instant());
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
