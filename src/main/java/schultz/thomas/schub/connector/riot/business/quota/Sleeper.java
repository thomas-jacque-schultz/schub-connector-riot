package schultz.thomas.schub.connector.riot.business.quota;

import java.time.Duration;

/**
 * L'attente, rendue injectable.
 *
 * <p>Sans cette indirection, tester l'étalement demanderait d'attendre réellement deux minutes,
 * et un test qui dort n'est pas un test.</p>
 */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
