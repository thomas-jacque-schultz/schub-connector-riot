package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.HistorySyncReport;
import schultz.thomas.schub.connector.riot.api.dto.MatchDetailsResponse;
import schultz.thomas.schub.connector.riot.api.dto.MatchHistory;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.ingest.IngestService;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.PlayerHistoryCursor;
import schultz.thomas.schub.connector.riot.data.model.PlayerMatchRef;
import schultz.thomas.schub.connector.riot.data.repository.PlayerHistoryCursorRepository;
import schultz.thomas.schub.connector.riot.data.repository.PlayerMatchRefRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
public class MatchHistoryService {

    private final RiotApiClient riotApiClient;
    private final PlayerMatchRefRepository playerMatches;
    private final PlayerHistoryCursorRepository cursors;
    private final MatchDetailService matchDetailService;
    private final IngestService ingestService;
    private final RiotProperties properties;
    private final Clock clock;

    public MatchHistory history(String puuid, Instant since) {
        boolean queued = needsSync(puuid) && ingestService.enqueuePlayer(puuid).queued();

        Instant floor = since == null ? Instant.EPOCH : since;
        List<String> matchIds = knownSince(puuid, floor);
        Instant syncedAt = cursors.findById(puuid)
                .map(PlayerHistoryCursor::lastSyncStartedAt)
                .orElse(null);

        return new MatchHistory(puuid, floor, matchIds, syncedAt, queued);
    }

    public HistorySyncReport sync(String puuid) {
        IdSyncResult ids = syncIds(puuid);
        MatchDetailsResponse details = fetchPendingDetails(puuid);

        log.info("Historique synchronisé : {} ids vus, {} nouveaux, {} détails récupérés, {} en attente.",
                ids.seen().size(), ids.created(), details.matches().size(), details.pending().size());

        return new HistorySyncReport(puuid, ids.queriedFrom(), ids.seen().size(), ids.created(),
                details.matches().size(), details.pending().size(), ids.startedAt());
    }

    public IdSyncResult syncIds(String puuid) {
        Instant startedAt = clock.instant();
        Optional<PlayerHistoryCursor> cursor = cursors.findById(puuid);
        Instant queriedFrom = queryFloor(cursor, startedAt);

        List<String> seen = collectIds(puuid, queriedFrom);
        int created = recordNewReferences(puuid, seen, startedAt);

        cursors.save(new PlayerHistoryCursor(
                puuid,
                cursor.map(PlayerHistoryCursor::firstSyncAt).orElse(startedAt),
                startedAt,
                newestKnownMatchAt(puuid)));

        return new IdSyncResult(puuid, queriedFrom, seen, created, startedAt);
    }

    private boolean needsSync(String puuid) {
        return cursors.findById(puuid)
                .map(cursor -> cursor.lastSyncStartedAt()
                        .plus(properties.getCache().getHistoryFreshness())
                        .isBefore(clock.instant()))
                .orElse(true);
    }

    private Instant queryFloor(Optional<PlayerHistoryCursor> cursor, Instant now) {
        return cursor
                .map(value -> value.lastSyncStartedAt().minus(properties.getCache().getHistoryOverlap()))
                .orElseGet(() -> now.minus(properties.getCache().getHistoryDepth()));
    }

    private List<String> collectIds(String puuid, Instant from) {
        int pageSize = properties.getCache().getIdPageSize();
        Set<String> ids = new LinkedHashSet<>();

        for (int page = 0; page < properties.getCache().getMaxIdPages(); page++) {
            List<String> batch = riotApiClient.matchIds(puuid, from, page * pageSize, pageSize);
            ids.addAll(batch);
            if (batch.size() < pageSize) {
                return List.copyOf(ids);
            }
        }
        log.warn("Garde-fou de pagination atteint ({} pages) : l'historique sera complété au prochain passage.",
                properties.getCache().getMaxIdPages());
        return List.copyOf(ids);
    }

    private int recordNewReferences(String puuid, List<String> matchIds, Instant discoveredAt) {
        List<String> keys = matchIds.stream().map(id -> PlayerMatchRef.idOf(puuid, id)).toList();
        Set<String> existing = new LinkedHashSet<>();
        playerMatches.findAllById(keys).forEach(reference -> existing.add(reference.id()));

        List<PlayerMatchRef> created = matchIds.stream()
                .filter(id -> !existing.contains(PlayerMatchRef.idOf(puuid, id)))
                .map(id -> new PlayerMatchRef(PlayerMatchRef.idOf(puuid, id), puuid, id, null, discoveredAt))
                .toList();

        if (!created.isEmpty()) {
            playerMatches.saveAll(created);
        }
        return created.size();
    }

    private MatchDetailsResponse fetchPendingDetails(String puuid) {
        List<String> undated = playerMatches.findByPuuidAndPlayedAtIsNull(puuid).stream()
                .map(PlayerMatchRef::matchId)
                .toList();
        if (undated.isEmpty()) {
            return new MatchDetailsResponse(List.of(), List.of(), List.of());
        }
        return matchDetailService.details(undated);
    }

    private List<String> knownSince(String puuid, Instant since) {
        List<PlayerMatchRef> dated =
                playerMatches.findByPuuidAndPlayedAtGreaterThanEqualOrderByPlayedAtDesc(puuid, since);
        List<PlayerMatchRef> undated = playerMatches.findByPuuidAndPlayedAtIsNull(puuid);

        List<PlayerMatchRef> all = new ArrayList<>(undated);
        all.addAll(dated);
        return all.stream()
                .sorted(Comparator.comparing(PlayerMatchRef::playedAt,
                        Comparator.nullsFirst(Comparator.reverseOrder())))
                .map(PlayerMatchRef::matchId)
                .distinct()
                .toList();
    }

    private Instant newestKnownMatchAt(String puuid) {
        return playerMatches.findFirstByPuuidAndPlayedAtNotNullOrderByPlayedAtDesc(puuid)
                .map(PlayerMatchRef::playedAt)
                .orElse(null);
    }
}
