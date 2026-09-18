package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.PlayerIdentity;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotResourceNotFoundException;
import schultz.thomas.schub.connector.riot.business.mapper.RiotStatsMapper;

/**
 * Résolution d'identité : {@code Pseudo#TAG} ⇄ {@code puuid}.
 *
 * <p><strong>Volontairement sans cache.</strong> Les quatre politiques du connecteur portent sur
 * des données de jeu ; une identité n'en est pas une. Un Riot ID peut changer, et garder une
 * correspondance périmée reviendrait à répondre faux — pour économiser un appel qui n'a lieu
 * qu'à la liaison d'un compte. Le {@code puuid}, lui, est stable : c'est le cœur qui le
 * conserve, sur le {@code User}.</p>
 */
@RequiredArgsConstructor
@Service
public class PlayerIdentityService {

    private final RiotApiClient riotApiClient;
    private final RiotStatsMapper mapper;

    /** {@code Pseudo#TAG} → identité. C'est l'appel de la liaison de compte (lot D.3). */
    public PlayerIdentity resolve(String gameName, String tagLine) {
        return riotApiClient.accountByRiotId(gameName, tagLine)
                .map(mapper::toIdentity)
                .orElseThrow(() -> new RiotResourceNotFoundException(
                        "Aucun compte Riot pour « " + gameName + "#" + tagLine + " »."));
    }

    /**
     * {@code puuid} → identité courante.
     *
     * <p>C'est par là qu'on rattrape un changement de Riot ID : le cœur a stocké le puuid, il
     * redemande le pseudo quand il veut l'afficher.</p>
     */
    public PlayerIdentity identify(String puuid) {
        return riotApiClient.accountByPuuid(puuid)
                .map(mapper::toIdentity)
                .orElseThrow(() -> new RiotResourceNotFoundException(
                        "Aucun compte Riot pour ce puuid."));
    }
}
