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

/**
 * Politique n°2 — <strong>append-only, incrémental par {@code startTime}</strong>.
 *
 * <h2>Le point de mise en œuvre à ne pas rater</h2>
 *
 * <p>L'historique d'un joueur ne fait que s'allonger : on ne redemande jamais une plage déjà
 * relevée, seulement ce qui la suit. Mais <strong>une partie peut apparaître dans l'historique
 * avec du retard</strong>. On repart donc du dernier relevé <em>moins une heure</em>, et on
 * dédoublonne sur le {@code matchId}.</p>
 *
 * <p>Le recouvrement coûte une lecture Mongo ; son absence coûte des parties manquantes, qu'on
 * ne voit jamais puisqu'on ignore qu'elles existent. C'est le genre de bug qui ne se manifeste
 * que par des statistiques légèrement fausses, et qu'on ne cherche donc jamais.</p>
 *
 * <p>Le curseur retient l'instant où la synchronisation a <em>commencé</em>, pas celui où elle
 * s'est terminée : une partie jouée pendant la synchronisation tomberait sinon dans l'angle
 * mort entre les deux.</p>
 *
 * <h2>Ce que ce service ne fait pas</h2>
 *
 * <p>Il ne sait pas ce qu'est une équipe, ni qu'une partie « compte » quand au moins 4 des 5
 * membres y ont joué. Il rend les identifiants d'un joueur ; le croisement est du domaine.</p>
 */
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

    /**
     * Les identifiants connus pour ce joueur depuis une date, servis du cache.
     *
     * <p>Une lecture ne collecte jamais elle-même : elle <em>empile</em>, et seulement si le
     * relevé est plus vieux que la fraîcheur configurée. Collecter ici mettrait un second
     * collecteur en concurrence avec l'ouvrier sur le même curseur et le même quota, et ferait
     * pendre un GET le temps que le quota s'écoule.</p>
     */
    public MatchHistory history(String puuid, Instant since) {
        boolean queued = needsSync(puuid) && ingestService.enqueuePlayer(puuid).queued();

        Instant floor = since == null ? Instant.EPOCH : since;
        List<String> matchIds = knownSince(puuid, floor);
        Instant syncedAt = cursors.findById(puuid)
                .map(PlayerHistoryCursor::lastSyncStartedAt)
                .orElse(null);

        return new MatchHistory(puuid, floor, matchIds, syncedAt, queued);
    }

    /**
     * Constitue ou prolonge l'historique d'un joueur.
     *
     * <p>Exposée en propre parce que le premier remplissage se fait en plusieurs passes : les
     * identifiants sont tous relevés d'un coup — c'est bon marché — mais les détails sont
     * récupérés par paquets bornés. Rappeler cette méthode reprend là où la précédente s'est
     * arrêtée.</p>
     */
    public HistorySyncReport sync(String puuid) {
        IdSyncResult ids = syncIds(puuid);
        MatchDetailsResponse details = fetchPendingDetails(puuid);

        log.info("Historique synchronisé : {} ids vus, {} nouveaux, {} détails récupérés, {} en attente.",
                ids.seen().size(), ids.created(), details.matches().size(), details.pending().size());

        return new HistorySyncReport(puuid, ids.queriedFrom(), ids.seen().size(), ids.created(),
                details.matches().size(), details.pending().size(), ids.startedAt());
    }

    /**
     * Le relevé d'identifiants seul — un appel par tranche de cent parties, donc bon marché.
     *
     * <p>C'est la voie de l'ingestion : les détails, eux, coûtent un appel chacun et passent
     * par la file plutôt que de pendre au bout d'une requête HTTP.</p>
     */
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

    /**
     * La borne envoyée à Riot : dernier relevé <strong>moins le recouvrement</strong>.
     *
     * <p>Sans curseur, on remonte de {@code historyDepth} — c'est le premier remplissage, et
     * c'est le seul moment où le quota se fait réellement sentir.</p>
     */
    private Instant queryFloor(Optional<PlayerHistoryCursor> cursor, Instant now) {
        return cursor
                .map(value -> value.lastSyncStartedAt().minus(properties.getCache().getHistoryOverlap()))
                .orElseGet(() -> now.minus(properties.getCache().getHistoryDepth()));
    }

    /** Pagine {@code match-v5} jusqu'à épuisement, sous un garde-fou de nombre de pages. */
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

    /**
     * Enregistre les identifiants inconnus. Le dédoublonnage est porté par la clé composite
     * {@code puuid#matchId} : le recouvrement d'une heure renvoie forcément des ids déjà vus,
     * et les écarter ne demande aucune vigilance à l'écriture.
     */
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

    /** Les parties dont on connaît l'id mais pas encore le détail, par paquets bornés. */
    private MatchDetailsResponse fetchPendingDetails(String puuid) {
        List<String> undated = playerMatches.findByPuuidAndPlayedAtIsNull(puuid).stream()
                .map(PlayerMatchRef::matchId)
                .toList();
        if (undated.isEmpty()) {
            return new MatchDetailsResponse(List.of(), List.of(), List.of());
        }
        return matchDetailService.details(undated);
    }

    /**
     * Les identifiants connus depuis une date, les non datés compris.
     *
     * <p>Une partie dont on n'a pas encore le détail n'a pas de date. La taire reviendrait à
     * cacher exactement ce qui manque ; on la rend, et le cœur la redemandera.</p>
     */
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
        return playerMatches.findByPuuidOrderByPlayedAtDesc(puuid).stream()
                .map(PlayerMatchRef::playedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
