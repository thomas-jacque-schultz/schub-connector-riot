package schultz.thomas.schub.connector.riot.business.quota;

import lombok.extern.slf4j.Slf4j;

import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.config.RiotProperties;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les limites propres à chaque route Riot, en plus de celle de l'application : une page de classement par
 * division (50 / 10 s) sature bien avant la clé (20 000 / 10 s). Un 429 « method » ne suspend que sa route.
 */
@Slf4j
public class MethodRateLimiter {

    private final Map<String, List<RiotProperties.Window>> limites;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Map<String, Route> routes = new ConcurrentHashMap<>();

    public MethodRateLimiter(Map<String, List<RiotProperties.Window>> limites, Clock clock, Sleeper sleeper) {
        this.limites = limites;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public void acquire(String method, Duration timeout) {
        Route route = route(method);
        long limite = clock.millis() + timeout.toMillis();
        while (true) {
            long attente = route.reserveOuAttente(clock.millis());
            if (attente <= 0) {
                return;
            }
            if (clock.millis() + attente > limite) {
                throw new RiotQuotaExceededException(
                        "Limite de la route " + method + " atteinte : il faudrait attendre " + Duration.ofMillis(attente)
                                + ".", Duration.ofMillis(attente));
            }
            try {
                sleeper.sleep(Duration.ofMillis(attente));
            } catch (InterruptedException interrompu) {
                Thread.currentThread().interrupt();
                throw new RiotQuotaExceededException("Attente du quota de " + method + " interrompue.", Duration.ZERO);
            }
        }
    }

    public void penalise(String method, Duration retryAfter) {
        route(method).penalise(clock.millis() + retryAfter.toMillis());
        log.warn("429 de Riot sur la route {} : elle seule est suspendue pendant {}", method, retryAfter);
    }

    // Une route sans limite propre existe quand même : un 429 « method » peut la suspendre.
    private Route route(String method) {
        return routes.computeIfAbsent(method, cle -> new Route(limites.getOrDefault(cle, List.of())));
    }

    private static final class Route {

        private final List<RiotProperties.Window> fenetres;
        private final List<Deque<Long>> creneaux;
        private long penaliseeJusqua;

        Route(List<RiotProperties.Window> fenetres) {
            this.fenetres = fenetres;
            this.creneaux = fenetres.stream().map(fenetre -> (Deque<Long>) new ArrayDeque<Long>()).toList();
        }

        synchronized long reserveOuAttente(long maintenant) {
            if (penaliseeJusqua > maintenant) {
                return penaliseeJusqua - maintenant;
            }
            long attente = 0;
            for (int i = 0; i < fenetres.size(); i++) {
                RiotProperties.Window fenetre = fenetres.get(i);
                Deque<Long> pris = creneaux.get(i);
                long horizon = maintenant - fenetre.getWindow().toMillis();
                while (!pris.isEmpty() && pris.peekFirst() <= horizon) {
                    pris.removeFirst();
                }
                if (pris.size() >= fenetre.getRequests()) {
                    attente = Math.max(attente, pris.peekFirst() + fenetre.getWindow().toMillis() - maintenant);
                }
            }
            if (attente > 0) {
                return attente;
            }
            creneaux.forEach(pris -> pris.addLast(maintenant));
            return 0;
        }

        synchronized void penalise(long jusqua) {
            penaliseeJusqua = Math.max(penaliseeJusqua, jusqua);
        }
    }
}
