package schultz.thomas.schub.connector.riot.business.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotConnectorBusyException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L'étalement des appels, vérifié sans attendre.
 *
 * <p>L'horloge et l'attente sont injectées : le limiteur croit avoir dormi, le test n'a pas
 * perdu une seconde.</p>
 */
class RiotRateLimiterTest {

    private TestClock clock;
    private List<Duration> attentes;
    private RiotProperties.Quota quota;

    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2026-09-18T12:00:00Z"));
        attentes = new ArrayList<>();
        quota = new RiotProperties.Quota();
        quota.setSafetyMargin(0);
        quota.setInteractiveReserve(0);
        quota.setBurstRequests(5);
        quota.setBurstWindow(Duration.ofSeconds(1));
        quota.setSustainedRequests(20);
        quota.setSustainedWindow(Duration.ofMinutes(2));
    }

    private RiotRateLimiter limiteur() {
        return new RiotRateLimiter(quota, clock, duree -> {
            attentes.add(duree);
            clock.advance(duree);
        });
    }

    @Test
    @DisplayName("la fenêtre courte laisse passer son quota, puis fait attendre")
    void tientLaFenetreCourte() {
        RiotRateLimiter limiteur = limiteur();

        for (int i = 0; i < 5; i++) {
            limiteur.acquire(QuotaLane.BULK);
        }
        assertThat(attentes).isEmpty();

        limiteur.acquire(QuotaLane.BULK);
        assertThat(attentes).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("la fenêtre longue impose un régime moyen : c'est elle qui étale le premier remplissage")
    void tientLaFenetreLongue() {
        RiotRateLimiter limiteur = limiteur();

        // 20 appels autorisés sur deux minutes. Les cinq premiers passent d'un bloc, puis la
        // fenêtre courte les espace ; au 21e, c'est la fenêtre LONGUE qui prend le relais et
        // l'attente devient de l'ordre de la minute.
        for (int i = 0; i < 20; i++) {
            limiteur.acquire(QuotaLane.BULK);
        }
        attentes.clear();

        limiteur.acquire(QuotaLane.BULK);

        assertThat(attentes).isNotEmpty();
        assertThat(attentes.get(attentes.size() - 1)).isGreaterThan(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("la marge de sécurité réduit réellement le quota utilisable")
    void appliqueLaMargeDeSecurite() {
        quota.setSafetyMargin(2);
        RiotRateLimiter limiteur = limiteur();

        for (int i = 0; i < 3; i++) {
            limiteur.acquire(QuotaLane.BULK);
        }
        assertThat(attentes).isEmpty();

        // 5 - 2 = 3 : le quatrième attend, là où sans marge il serait passé.
        limiteur.acquire(QuotaLane.BULK);
        assertThat(attentes).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("un 429 suspend tous les appels pour la durée demandée par Riot")
    void respecteRetryAfter() {
        RiotRateLimiter limiteur = limiteur();

        // Valeur relevée sur un vrai 429 provoqué le 18-09 : « retry-after: 2 ».
        limiteur.penalise(Duration.ofSeconds(2));
        limiteur.acquire(QuotaLane.BULK);

        assertThat(attentes).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("un Retry-After aberrant est plafonné : le connecteur ne se gèle pas pour une heure")
    void plafonneUnRetryAfterAberrant() {
        quota.setMaxRetryAfter(Duration.ofMinutes(3));
        RiotRateLimiter limiteur = limiteur();

        limiteur.penalise(Duration.ofHours(1));
        limiteur.acquire(QuotaLane.BULK);

        assertThat(attentes).containsExactly(Duration.ofMinutes(3));
    }

    @Test
    @DisplayName("au-delà du délai accepté, l'appelant reçoit un refus plutôt qu'une attente sans fin")
    void refusePlutotQueDeFairePatienterIndefiniment() {
        quota.setAcquireTimeout(Duration.ofSeconds(30));
        RiotRateLimiter limiteur = limiteur();

        limiteur.penalise(Duration.ofMinutes(2));

        assertThatThrownBy(() -> limiteur.acquire(QuotaLane.BULK))
                .isInstanceOf(RiotQuotaExceededException.class)
                .hasMessageContaining("Quota Riot saturé");
        assertThat(attentes).isEmpty();
    }

    @Test
    @DisplayName("un appel interactif qui ne peut pas être servi vite renonce sans dormir, et dit « occupé »")
    void lInteractifRenonceViteEtDitOccupe() {
        quota.setInteractiveTimeout(Duration.ofSeconds(2));
        RiotRateLimiter limiteur = limiteur();

        limiteur.penalise(Duration.ofSeconds(30));

        assertThatThrownBy(() -> limiteur.acquire(QuotaLane.INTERACTIVE))
                .isInstanceOf(RiotConnectorBusyException.class)
                .isInstanceOf(RiotQuotaExceededException.class)
                .hasMessageContaining("Connecteur occupé");
        assertThat(attentes).isEmpty();
    }

    @Test
    @DisplayName("là où l'interactif renonce, la collecte attend : ce sont deux délais distincts")
    void laCollecteAttendLaOuLInteractifRenonce() {
        quota.setInteractiveTimeout(Duration.ofSeconds(2));
        quota.setAcquireTimeout(Duration.ofMinutes(5));
        RiotRateLimiter limiteur = limiteur();

        limiteur.penalise(Duration.ofSeconds(30));
        limiteur.acquire(QuotaLane.BULK);

        assertThat(attentes).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("la pénalité d'un 429 vaut aussi pour la voie interactive : le quota est unique")
    void la429SuspendAussiLInteractif() {
        quota.setInteractiveTimeout(Duration.ofSeconds(5));
        RiotRateLimiter limiteur = limiteur();

        limiteur.penalise(Duration.ofSeconds(2));
        limiteur.acquire(QuotaLane.INTERACTIVE);

        assertThat(attentes).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("la fenêtre glisse : passé son terme, le quota se reconstitue")
    void laFenetreGlisse() {
        RiotRateLimiter limiteur = limiteur();

        for (int i = 0; i < 5; i++) {
            limiteur.acquire(QuotaLane.BULK);
        }
        clock.advance(Duration.ofSeconds(2));

        limiteur.acquire(QuotaLane.BULK);
        assertThat(attentes).isEmpty();
    }
}
