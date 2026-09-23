package schultz.thomas.schub.connector.riot.business.exceptions;

import java.time.Duration;

// Sous-classe de RiotQuotaExceededException : les chemins à réponse partielle la traitent sans changement.
public class RiotConnectorBusyException extends RiotQuotaExceededException {

    public RiotConnectorBusyException(String message, Duration retryAfter) {
        super(message, retryAfter);
    }
}
