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

class RiotRateLimiterReserveTest {

    private static final int PAS_MS = 100;

    private TestClock clock;
    private List<Duration> attentes;
    private RiotProperties.Quota quota;

    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2026-09-22T12:00:00Z"));
        attentes = new ArrayList<>();
        quota = new RiotProperties.Quota();
        quota.setAcquireTimeout(Duration.ZERO);
        quota.setWindowGuard(Duration.ZERO);
    }

    @Test
    @DisplayName("un appel interactif toutes les trois secondes passe alors que la collecte sature")
    void lInteractifPasseSousChargeSoutenue() {
        RiotRateLimiter limiteur = limiteur();
        int servis = 0;

        for (int ms = 0; ms <= 60_000; ms += PAS_MS) {
            collecte(limiteur);
            if (ms > 0 && ms % 3_000 == 0 && interactif(limiteur)) {
                servis++;
            }
            clock.advance(Duration.ofMillis(PAS_MS));
        }

        assertThat(servis).as("la mesure du 22-09 donnait 0 servi sur 20").isEqualTo(20);
        assertThat(attentes).as("servi tout de suite, pas au bout des deux secondes").isEmpty();
        assertThat(limiteur.granted(QuotaLane.BULK))
                .as("et la collecte a bien tourné pendant ce temps").isGreaterThan(40);
    }

    @Test
    @DisplayName("la réserve est une part du compteur, pas un supplément")
    void laReserveNEstPasUnSupplement() {
        RiotRateLimiter limiteur = limiteur();

        for (int ms = 0; ms < 100_000; ms += PAS_MS) {
            collecte(limiteur);
            interactif(limiteur);
            clock.advance(Duration.ofMillis(PAS_MS));
        }

        assertThat(limiteur.granted(QuotaLane.BULK) + limiteur.granted(QuotaLane.INTERACTIVE))
                .as("100 par deux minutes, moins deux de marge, les deux voies réunies")
                .isEqualTo(98);
        assertThat(interactif(limiteur)).as("le 99e appel de la fenêtre est refusé").isFalse();
    }

    @Test
    @DisplayName("une réserve que personne ne demande revient à la collecte, à un créneau près")
    void laReserveInutiliseeRevientALaCollecte() {
        RiotRateLimiter limiteur = limiteur();
        assertThat(interactif(limiteur)).isTrue();

        collecterJusqua(limiteur, 59_000);
        assertThat(limiteur.granted(QuotaLane.BULK))
                .as("réserve armée : 88 créneaux par deux minutes, soit 44 par minute")
                .isEqualTo(43);

        collecterJusqua(limiteur, 90_000);
        assertThat(limiteur.granted(QuotaLane.BULK))
                .as("plus personne ne demande : la collecte reprend les dix créneaux, sauf un")
                .isEqualTo(96);
        assertThat(interactif(limiteur))
                .as("ce créneau-là sert la demande qui revient, sans attendre la fenêtre").isTrue();
    }

    @Test
    @DisplayName("la réserve ne dispense pas de la pénalité d'un 429")
    void laReserveNeDispensePasDeLaPenalite() {
        RiotRateLimiter limiteur = limiteur();
        assertThat(interactif(limiteur)).isTrue();

        limiteur.penalise(Duration.ofSeconds(30));

        assertThat(interactif(limiteur)).as("occupé, et non servi par la réserve").isFalse();
        assertThat(collecte(limiteur)).isFalse();
        assertThat(attentes).as("refusé tout de suite, sans dormir").isEmpty();
    }

    private RiotRateLimiter limiteur() {
        return new RiotRateLimiter(quota, clock, duree -> {
            attentes.add(duree);
            clock.advance(duree);
        });
    }

    private void collecterJusqua(RiotRateLimiter limiteur, int finMs) {
        Instant fin = Instant.parse("2026-09-22T12:00:00Z").plusMillis(finMs);
        while (clock.instant().isBefore(fin)) {
            collecte(limiteur);
            clock.advance(Duration.ofMillis(PAS_MS));
        }
    }

    private boolean collecte(RiotRateLimiter limiteur) {
        try {
            limiteur.acquire(QuotaLane.BULK);
            return true;
        } catch (RiotQuotaExceededException sature) {
            return false;
        }
    }

    private boolean interactif(RiotRateLimiter limiteur) {
        try {
            limiteur.acquire(QuotaLane.INTERACTIVE);
            return true;
        } catch (RiotConnectorBusyException occupe) {
            return false;
        }
    }
}
