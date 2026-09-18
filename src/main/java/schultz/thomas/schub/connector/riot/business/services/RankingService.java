package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.mapper.RiotStatsMapper;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.CachedRanking;
import schultz.thomas.schub.connector.riot.data.repository.CachedRankingRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Politique n°4 — <strong>TTL ~ 1 h</strong>, la donnée la plus volatile du connecteur.
 *
 * <p>Afficher un LP d'hier serait un bug visible ; rafraîchir à chaque affichage brûlerait le
 * quota pour un chiffre qui bouge quelques fois par jour. Une heure est le compromis retenu.</p>
 *
 * <p>L'entrée périmée n'est jamais supprimée : quand Riot ne répond pas, elle est servie telle
 * quelle. {@code observedAt} accompagne chaque relevé, donc l'appelant sait ce qu'il affiche —
 * une donnée de cache qui tait son âge est une donnée fausse.</p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RankingService {

    private final RiotApiClient riotApiClient;
    private final CachedRankingRepository rankings;
    private final RiotStatsMapper mapper;
    private final RiotProperties properties;
    private final Clock clock;

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
            return fresh;
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
