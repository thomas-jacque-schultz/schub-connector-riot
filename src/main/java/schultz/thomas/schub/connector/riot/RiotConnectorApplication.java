package schultz.thomas.schub.connector.riot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Connecteur Riot : appels à l'API Riot Games.
 */
@SpringBootApplication
public class RiotConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiotConnectorApplication.class, args);
    }
}
