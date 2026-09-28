package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.exceptions.StalePuuidException;
import schultz.thomas.schub.connector.riot.business.ingest.IngestQueue;
import schultz.thomas.schub.connector.riot.business.mapper.RiotStatsMapper;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.CachedRanking;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.repository.CachedRankingRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class RankingService {

    private final RiotApiClient riotApiClient;
    private final CachedRankingRepository rankings;
    private final RiotStatsMapper mapper;
    private final RiotProperties properties;
    private final RankHistory history;
    private final IngestQueue queue;
    private final Clock clock;

    // Pour un écran : le dernier relevé sans attendre Riot ; Riot n'est appelé en direct que pour un joueur jamais relevé.
    public List<RankedStanding> current(String puuid) {
        Optional<CachedRanking> cached = rankings.findById(puuid);
        if (cached.isEmpty()) {
            return rankings(puuid);
        }
        if (cached.get().fetchedAt().plus(properties.getCache().getRankingRefreshAfter()).isBefore(clock.instant())) {
            queue.enqueue(IngestTaskType.PLAYER_RANK, puuid, puuid, 0);
        }
        return cached.get().standings();
    }

    public List<RankedStanding> rankings(String puuid) {
        Instant now = clock.instant();
        Optional<CachedRanking> cached = rankings.findById(puuid);

        if (cached.isPresent()
                && cached.get().fetchedAt().plus(properties.getCache().getRankingTtl()).isAfter(now)) {
            return cached.get().standings();
        }

        try {
            List<RankedStanding> fresh = riotApiClient.leagueEntries(puuid).stream()
                    .map(entry -> mapper.toStanding(entry, now))
                    .toList();
            rankings.save(new CachedRanking(puuid, fresh, now));
            history.record(puuid, fresh);
            return fresh;
        } catch (StalePuuidException perime) {
            throw perime;
        } catch (RuntimeException failure) {
            if (cached.isPresent()) {
                log.warn("Classement indisponible pour ce joueur, relevé du {} servi : {}",
                        cached.get().fetchedAt(), failure.getMessage());
                return cached.get().standings();
            }
            throw failure;
        }
    }
}
