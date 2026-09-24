package schultz.thomas.schub.connector.riot.business.quota;

import schultz.thomas.schub.connector.riot.config.RiotProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Les limites que Riot annonce dans X-App-Rate-Limit et X-Method-Rate-Limit (« 100:120,20:1 » : requêtes:secondes),
 * ou le décompte en cours dans les en-têtes -Count, de même forme. De la fenêtre la plus courte à la plus longue ;
 * une annonce illisible donne une liste vide.
 */
public final class LimitesAnnoncees {

    private LimitesAnnoncees() {
    }

    public static List<RiotProperties.Window> lire(String entete) {
        if (entete == null || entete.isBlank()) {
            return List.of();
        }
        List<RiotProperties.Window> fenetres = new ArrayList<>();
        for (String paire : entete.split(",")) {
            String[] morceaux = paire.trim().split(":");
            if (morceaux.length != 2) {
                return List.of();
            }
            try {
                int requetes = Integer.parseInt(morceaux[0].trim());
                long secondes = Long.parseLong(morceaux[1].trim());
                if (requetes < 0 || secondes <= 0) {
                    return List.of();
                }
                fenetres.add(new RiotProperties.Window(requetes, Duration.ofSeconds(secondes)));
            } catch (NumberFormatException illisible) {
                return List.of();
            }
        }
        fenetres.sort(Comparator.comparing(RiotProperties.Window::getWindow));
        return List.copyOf(fenetres);
    }
}
