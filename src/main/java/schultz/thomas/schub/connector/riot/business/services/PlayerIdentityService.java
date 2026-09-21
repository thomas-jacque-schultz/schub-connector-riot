package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.PlayerIdentity;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotResourceNotFoundException;
import schultz.thomas.schub.connector.riot.business.mapper.RiotStatsMapper;
import schultz.thomas.schub.connector.riot.business.search.KnownAccountIndex;

/**
 * Résolution d'identité : {@code Pseudo#TAG} ⇄ {@code puuid}.
 *
 * <h2>Toujours sans cache — mais plus sans trace</h2>
 *
 * <p><strong>Aucune correspondance n'est mise en cache</strong> : chaque résolution appelle Riot,
 * parce qu'un Riot ID change et que servir une correspondance périmée reviendrait à répondre
 * faux. Ce qui est écrit est autre chose : une <em>observation datée</em> dans l'index des
 * comptes connus. « Le 21-09, ce puuid s'appelait X » reste vrai demain, quel que soit le nom
 * qu'il portera.</p>
 *
 * <p>Cette écriture est ce qui permet à un compte vérifié d'apparaître dans la recherche, et elle
 * est ici plutôt que sur une route dédiée pour une raison simple : <strong>toute</strong>
 * résolution enrichit l'index, quelle qu'en soit la raison, et il n'existe donc aucun chemin qui
 * demande à Riot sans rien en retenir.</p>
 */
@RequiredArgsConstructor
@Service
public class PlayerIdentityService {

    private final RiotApiClient riotApiClient;
    private final RiotStatsMapper mapper;
    private final KnownAccountIndex knownAccounts;

    /** {@code Pseudo#TAG} → identité. C'est l'appel de la liaison de compte (lot D.3). */
    public PlayerIdentity resolve(String gameName, String tagLine) {
        return observe(riotApiClient.accountByRiotId(gameName, tagLine)
                .map(mapper::toIdentity)
                .orElseThrow(() -> new RiotResourceNotFoundException(
                        "Aucun compte Riot pour « " + gameName + "#" + tagLine + " ».")));
    }

    /**
     * {@code puuid} → identité courante.
     *
     * <p>C'est par là qu'on rattrape un changement de Riot ID : le cœur a stocké le puuid, il
     * redemande le pseudo quand il veut l'afficher.</p>
     */
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
