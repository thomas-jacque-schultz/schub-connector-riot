package schultz.thomas.schub.connector.riot.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Une horloge qu'on avance à la main.
 *
 * <p>Tout ce que ce connecteur décide — un TTL est-il expiré, faut-il attendre avant d'émettre —
 * dépend du temps. Le tester avec l'horloge du système demanderait d'attendre réellement une
 * heure, et un test qui dort n'est pas un test.</p>
 */
public class TestClock extends Clock {

    private Instant now;

    public TestClock(Instant start) {
        this.now = start;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
