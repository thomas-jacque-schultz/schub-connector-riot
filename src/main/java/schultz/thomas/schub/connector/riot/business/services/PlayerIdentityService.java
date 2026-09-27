package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.PlayerIdentity;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotResourceNotFoundException;
import schultz.thomas.schub.connector.riot.business.mapper.RiotStatsMapper;
import schultz.thomas.schub.connector.riot.business.search.KnownAccountIndex;

// Pas de cache Pseudo#TAG → puuid (un Riot ID change) : toute résolution laisse une observation datée dans
// l'index des comptes connus. Seul l'appelant qui le demande (maxAge) accepte une résolution récente.
@RequiredArgsConstructor
@Service
public class PlayerIdentityService {

    private final RiotApiClient riotApiClient;
    private final RiotStatsMapper mapper;
    private final KnownAccountIndex knownAccounts;

    public PlayerIdentity resolve(String gameName, String tagLine) {
        return observe(riotApiClient.accountByRiotId(gameName, tagLine)
                .map(mapper::toIdentity)
                .orElseThrow(() -> new RiotResourceNotFoundException(
                        "Aucun compte Riot pour « " + gameName + "#" + tagLine + " ».")));
    }

    public PlayerIdentity resolve(String gameName, String tagLine, java.time.Duration maxAge) {
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            return resolve(gameName, tagLine);
        }
        return knownAccounts.recentResolution(gameName, tagLine, maxAge)
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
