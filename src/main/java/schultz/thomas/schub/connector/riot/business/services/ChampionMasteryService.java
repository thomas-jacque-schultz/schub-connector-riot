package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.ChampionMastery;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.mapper.RiotStatsMapper;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.CachedMastery;
import schultz.thomas.schub.connector.riot.data.repository.CachedMasteryRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Politique n°3 — <strong>TTL ~ 6 h</strong>.
 *
 * <p>Une maîtrise évolue en jouant, donc lentement à l'échelle d'une journée. Un pool de
 * champions n'a pas besoin d'être à la seconde : six heures suffisent, et c'est cinq appels
 * par équipe et par demi-journée au lieu d'un par affichage.</p>
 *
 * <p>Le connecteur rend des maîtrises, pas un « pool jouable par poste » : décider qu'un joueur
 * peut tenir le poste de jungle est un jugement de domaine, et il vit dans le cœur.</p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ChampionMasteryService {

    private final RiotApiClient riotApiClient;
    private final CachedMasteryRepository masteries;
    private final ChampionCatalogService catalogService;
    private final RiotStatsMapper mapper;
    private final RiotProperties properties;
    private final Clock clock;

    /**
     * @param limit nombre de champions rendus, du plus maîtrisé au moins maîtrisé.
     *              {@code null} ou ≤ 0 = tout. Le filtrage est appliqué à la lecture, pas au
     *              stockage : on garde tout en base, sinon demander 20 champions après en avoir
     *              demandé 5 relancerait un appel pour une donnée déjà payée.
     */
    public List<ChampionMastery> masteries(String puuid, Integer limit) {
        List<ChampionMastery> all = allMasteries(puuid);
        if (limit == null || limit <= 0 || limit >= all.size()) {
            return all;
        }
        return all.subList(0, limit);
    }

    private List<ChampionMastery> allMasteries(String puuid) {
        Instant now = clock.instant();
        Optional<CachedMastery> cached = masteries.findById(puuid);

        if (cached.isPresent()
                && cached.get().fetchedAt().plus(properties.getCache().getMasteryTtl()).isAfter(now)) {
            return cached.get().masteries();
        }

        try {
            Map<Integer, String> names = catalogService.championNames();
            List<ChampionMastery> fresh = riotApiClient.masteries(puuid).stream()
                    .map(mastery -> mapper.toMastery(mastery, names.get(mastery.championId()), now))
                    .sorted(Comparator.comparingInt(ChampionMastery::points).reversed())
                    .toList();
            masteries.save(new CachedMastery(puuid, fresh, now));
            return fresh;
        } catch (RuntimeException failure) {
            if (cached.isPresent()) {
                log.warn("Maîtrises indisponibles pour ce joueur, relevé du {} servi : {}",
                        cached.get().fetchedAt(), failure.getMessage());
                return cached.get().masteries();
            }
            throw failure;
        }
    }
}
