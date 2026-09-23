package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.ChampionCard;
import schultz.thomas.schub.connector.riot.api.dto.ChampionCatalog;
import schultz.thomas.schub.connector.riot.business.client.DataDragonClient;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotApiException;
import schultz.thomas.schub.connector.riot.business.mapper.RiotStatsMapper;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.CachedChampionCatalog;
import schultz.thomas.schub.connector.riot.data.model.CachedGameVersion;
import schultz.thomas.schub.connector.riot.data.repository.CachedChampionCatalogRepository;
import schultz.thomas.schub.connector.riot.data.repository.CachedGameVersionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChampionCatalogService {

    private final DataDragonClient dataDragonClient;
    private final CachedChampionCatalogRepository catalogs;
    private final CachedGameVersionRepository gameVersions;
    private final RiotStatsMapper mapper;
    private final RiotProperties properties;
    private final java.time.Clock clock;

    public String currentVersion() {
        Instant now = clock.instant();
        Optional<CachedGameVersion> cached = gameVersions.findById(CachedGameVersion.CURRENT);

        if (cached.isPresent()
                && cached.get().fetchedAt().plus(properties.getCache().getGameVersionTtl()).isAfter(now)) {
            return cached.get().version();
        }

        try {
            List<String> versions = dataDragonClient.versions();
            if (versions.isEmpty()) {
                throw new RiotApiException("Data Dragon n'a rendu aucune version.");
            }
            String latest = versions.get(0);
            gameVersions.save(new CachedGameVersion(CachedGameVersion.CURRENT, latest, now));
            return latest;
        } catch (RiotApiException failure) {
            if (cached.isPresent()) {
                log.warn("Version Data Dragon indisponible, repli sur {} relevée le {}.",
                        cached.get().version(), cached.get().fetchedAt());
                return cached.get().version();
            }
            throw failure;
        }
    }

    public ChampionCatalog catalog(String version) {
        String resolved = (version == null || version.isBlank()) ? currentVersion() : version;
        String locale = properties.getDataDragonLocale();
        String id = CachedChampionCatalog.idOf(resolved, locale);

        Optional<CachedChampionCatalog> cached = catalogs.findById(id);
        if (cached.isPresent()) {
            return cached.get().catalog();
        }

        List<ChampionCard> champions = mapper.toCatalog(
                dataDragonClient.champions(resolved, locale),
                properties.getDataDragonBaseUrl(),
                resolved);

        ChampionCatalog catalog = new ChampionCatalog(resolved, locale, champions);
        catalogs.save(new CachedChampionCatalog(id, catalog, clock.instant()));
        return catalog;
    }

    public Map<Integer, String> championNames() {
        try {
            return catalog(null).champions().stream()
                    .filter(card -> card.id() > 0)
                    .collect(Collectors.toMap(ChampionCard::id, ChampionCard::name,
                            (first, second) -> first));
        } catch (RuntimeException failure) {
            log.warn("Catalogue indisponible, les maîtrises seront rendues sans nom de champion : {}",
                    failure.getMessage());
            return Map.of();
        }
    }

    public Map<String, ChampionCard> byKey(String version) {
        return catalog(version).champions().stream()
                .collect(Collectors.toMap(ChampionCard::key, Function.identity(),
                        (first, second) -> first));
    }
}
