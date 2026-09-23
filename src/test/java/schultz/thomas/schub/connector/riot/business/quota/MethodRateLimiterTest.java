package schultz.thomas.schub.connector.riot.business.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.config.RiotMethods;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.support.TestClock;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MethodRateLimiterTest {

    private TestClock clock;
    private MethodRateLimiter limiteur;

    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2026-09-23T12:00:00Z"));
        limiteur = new MethodRateLimiter(new RiotProperties().getQuota().getMethods(), clock, clock::advance);
    }

    @Test
    @DisplayName("la 51e page de classement en dix secondes attend que la première sorte de la fenêtre")
    void pageDeClassement() {
        Instant debut = clock.instant();
        for (int i = 0; i < 50; i++) {
            limiteur.acquire(RiotMethods.LEAGUE_DIVISION, Duration.ofMinutes(1));
        }
        assertThat(clock.instant()).isEqualTo(debut);

        limiteur.acquire(RiotMethods.LEAGUE_DIVISION, Duration.ofMinutes(1));

        assertThat(Duration.between(debut, clock.instant())).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("une route saturée ne retient pas les autres")
    void routesIndependantes() {
        for (int i = 0; i < 50; i++) {
            limiteur.acquire(RiotMethods.LEAGUE_DIVISION, Duration.ofMinutes(1));
        }
        Instant avant = clock.instant();

        limiteur.acquire(RiotMethods.MATCH, Duration.ofMinutes(1));

        assertThat(clock.instant()).isEqualTo(avant);
    }

    @Test
    @DisplayName("un 429 de route suspend cette route, même sans limite déclarée, et seulement elle")
    void penaliteCiblee() {
        limiteur.penalise(RiotMethods.MASTERY, Duration.ofSeconds(5));
        Instant avant = clock.instant();

        limiteur.acquire(RiotMethods.MATCH, Duration.ofMinutes(1));
        assertThat(clock.instant()).isEqualTo(avant);

        limiteur.acquire(RiotMethods.MASTERY, Duration.ofMinutes(1));
        assertThat(Duration.between(avant, clock.instant())).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("une attente au-delà du délai accepté est refusée plutôt que subie")
    void delaiDepasse() {
        limiteur.penalise(RiotMethods.MATCH, Duration.ofMinutes(2));

        assertThatThrownBy(() -> limiteur.acquire(RiotMethods.MATCH, Duration.ofSeconds(2)))
                .isInstanceOf(RiotQuotaExceededException.class);
    }
}
