package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.PlayerIdentity;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotResourceNotFoundException;
import schultz.thomas.schub.connector.riot.business.exceptions.StalePuuidException;
import schultz.thomas.schub.connector.riot.business.mapper.RiotStatsMapper;
import schultz.thomas.schub.connector.riot.business.search.KnownAccountIndex;

import java.util.Optional;

// Pas de cache Pseudo#TAG → puuid (un Riot ID change) : toute résolution laisse une observation datée dans
// l'index des comptes connus. Seul l'appelant qui le demande (maxAge) accepte une observation récente de l'index.
@Slf4j
@RequiredArgsConstructor
@Service
public class PlayerIdentityService {

    private final RiotApiClient riotApiClient;
    private final RiotStatsMapper mapper;
    private final KnownAccountIndex knownAccounts;

    public PlayerIdentity resolve(String gameName, String tagLine) {
        return observe(riotApiClient.accountByRiotId(gameName, tagLine)
                .map(mapper::toIdentity)
                .or(() -> renomme(gameName, tagLine))
                .orElseThrow(() -> new RiotResourceNotFoundException(
                        "Aucun compte Riot pour « " + gameName + "#" + tagLine + " ».")));
    }

    // Un Riot ID relevé dans une partie ancienne a pu changer depuis : Riot ne le connaît plus, mais le puuid est stable.
    // Le nom actuel du joueur est rendu, et l'observation corrige l'index de recherche.
    private Optional<PlayerIdentity> renomme(String gameName, String tagLine) {
        return knownAccounts.puuidsSeenAs(gameName, tagLine).stream().findFirst()
                .flatMap(puuid -> {
                    try {
                        return riotApiClient.accountByPuuid(puuid).map(mapper::toIdentity);
                    } catch (StalePuuidException autreCle) {
                        return Optional.empty();
                    }
                })
                .map(actuel -> {
                    log.info("Riot ID {}#{} renommé depuis en {}#{}", gameName, tagLine, actuel.gameName(),
                            actuel.tagLine());
                    return actuel;
                });
    }

    public PlayerIdentity resolve(String gameName, String tagLine, java.time.Duration maxAge) {
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            return resolve(gameName, tagLine);
        }
        return knownAccounts.recentObservation(gameName, tagLine, maxAge)
                .map(compte -> new PlayerIdentity(compte.puuid(), compte.gameName(), compte.tagLine()))
                .orElseGet(() -> resolve(gameName, tagLine));
    }

    public PlayerIdentity identify(String puuid) {
        return observe(riotApiClient.accountByPuuid(puuid)
                .map(mapper::toIdentity)
                .orElseThrow(() -> new RiotResourceNotFoundException(
                        "Aucun compte Riot pour ce puuid.")));
    }

    private PlayerIdentity observe(PlayerIdentity identity) {
        knownAccounts.observeResolution(identity.puuid(), identity.gameName(), identity.tagLine());
        return identity;
    }
}
