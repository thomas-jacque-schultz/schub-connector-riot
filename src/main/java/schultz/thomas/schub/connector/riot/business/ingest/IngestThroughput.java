package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

// Avec une clé de production, le quota ne borne plus rien : l'attente se lit sur ce que les ouvriers font vraiment.
@Component
@RequiredArgsConstructor
public class IngestThroughput {

    static final Duration FENETRE = Duration.ofMinutes(5);

    private final Clock clock;
    private final Deque<Long> faites = new ArrayDeque<>();

    public synchronized void record() {
        long maintenant = clock.millis();
        faites.addLast(maintenant);
        oublie(maintenant);
    }

    public synchronized double perMinute() {
        oublie(clock.millis());
        return faites.size() / (double) FENETRE.toMinutes();
    }

    private void oublie(long maintenant) {
        long horizon = maintenant - FENETRE.toMillis();
        while (!faites.isEmpty() && faites.peekFirst() <= horizon) {
            faites.removeFirst();
        }
    }
}
