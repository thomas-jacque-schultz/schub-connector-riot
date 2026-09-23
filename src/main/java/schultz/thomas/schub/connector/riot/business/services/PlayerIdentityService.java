package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.PlayerIdentity;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotResourceNotFoundException;
import schultz.thomas.schub.connector.riot.business.mapper.RiotStatsMapper;
import schultz.thomas.schub.connector.riot.business.search.KnownAccountIndex;

// Jamais de cache Pseudo#TAG → puuid (un Riot ID change), mais toute résolution laisse une observation
// datée dans l'index des comptes connus.
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
