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
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchParticipationRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Construit la couche d'analyse depuis {@code riot_match}. Aucun appel sortant n'est possible
 * d'ici, et c'est la garantie recherchée : changer le modèle ne coûte qu'une reconstruction.
 */
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

    /**
     * Projette les <strong>dix</strong> participants, pas seulement le joueur demandeur.
     *
     * <p>La partie est déjà payée : restreindre la projection obligerait à la refaire le jour
     * où un coéquipier lie son compte, et c'est le seul moment où l'information est gratuite.</p>
     */
    public int project(MatchDetail detail) {
        Instant now = clock.instant();
        List<MatchParticipation> rows = detail.participants().stream()
                .map(participant -> toParticipation(detail, participant, now))
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

    /** Reconstruction complète. Purge d'abord : un modèle qui perd un champ laisserait sinon des restes. */
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
        return new RebuildReport(matchesRead, rowsWritten, unusable, startedAt, clock.instant());
    }

    private static KnownAccountIndex.Observation toObservation(MatchParticipation row) {
        return new KnownAccountIndex.Observation(row.puuid(), row.gameName(), row.tagLine(),
                row.startedAt(), KnownAccountSource.PARTICIPATION);
    }

    private MatchParticipation toParticipation(MatchDetail detail, MatchParticipant participant,
                                               Instant now) {
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
                participant.visionScore(),
                participant.afk(),
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
