package schultz.thomas.schub.connector.riot.business.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotResourceNotFoundException;
import schultz.thomas.schub.connector.riot.business.mapper.RiotStatsMapper;
import schultz.thomas.schub.connector.riot.business.search.KnownAccountIndex;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotAccountResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Résoudre appelle Riot à chaque fois, et laisse une observation datée derrière soi : c'est ce
 * qui fait qu'un compte vérifié devient trouvable, pour tout le monde et pas seulement pour
 * celui qui l'a demandé.
 */
@ExtendWith(MockitoExtension.class)
class PlayerIdentityServiceTest {

    @Mock private RiotApiClient riotApiClient;
    @Mock private KnownAccountIndex knownAccounts;

    private PlayerIdentityService service;

    @BeforeEach
    void setUp() {
        service = new PlayerIdentityService(riotApiClient, new RiotStatsMapper(), knownAccounts);
    }

    @Test
    @DisplayName("une résolution réussie entre dans l'index des comptes connus")
    void enrichitLIndexQuandRiotConfirme() {
        when(riotApiClient.accountByRiotId("Thomas", "EUW"))
                .thenReturn(Optional.of(new RiotAccountResponse("p1", "Thomas", "EUW")));

        assertThat(service.resolve("Thomas", "EUW").puuid()).isEqualTo("p1");

        verify(knownAccounts).observeResolution("p1", "Thomas", "EUW");
    }

    @Test
    @DisplayName("un Riot ID inexistant lève et n'écrit rien")
    void nEcritRienQuandLeCompteNExistePas() {
        when(riotApiClient.accountByRiotId("Personne", "ZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("Personne", "ZZZ"))
                .isInstanceOf(RiotResourceNotFoundException.class);

        verify(knownAccounts, never()).observeResolution(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("le rattrapage d'un changement de Riot ID rafraîchit aussi l'index")
    void rafraichitLIndexEnIdentifiantParPuuid() {
        when(riotApiClient.accountByPuuid("p1"))
                .thenReturn(Optional.of(new RiotAccountResponse("p1", "NouveauNom", "FR1")));

        assertThat(service.identify("p1").riotId()).isEqualTo("NouveauNom#FR1");

        verify(knownAccounts).observeResolution("p1", "NouveauNom", "FR1");
    }
}
