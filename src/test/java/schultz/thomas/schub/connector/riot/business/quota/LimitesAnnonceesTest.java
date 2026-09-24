package schultz.thomas.schub.connector.riot.business.quota;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import schultz.thomas.schub.connector.riot.config.RiotProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LimitesAnnonceesTest {

    @Test
    @DisplayName("l'annonce d'une clé de développement donne ses deux fenêtres, la plus courte d'abord")
    void cleDeDeveloppement() {
        assertThat(LimitesAnnoncees.lire("100:120,20:1")).containsExactly(
                new RiotProperties.Window(20, Duration.ofSeconds(1)),
                new RiotProperties.Window(100, Duration.ofMinutes(2)));
    }

    @Test
    @DisplayName("une annonce absente ou mal formée ne donne rien")
    void annonceIllisible() {
        assertThat(LimitesAnnoncees.lire(null)).isEmpty();
        assertThat(LimitesAnnoncees.lire("")).isEmpty();
        assertThat(LimitesAnnoncees.lire("20:1,cent")).isEmpty();
        assertThat(LimitesAnnoncees.lire("0:10")).isEmpty();
    }
}
