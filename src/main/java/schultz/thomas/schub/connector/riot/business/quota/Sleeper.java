package schultz.thomas.schub.connector.riot.business.quota;

import java.time.Duration;

@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
