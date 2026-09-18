package schultz.thomas.schub.connector.riot.business.exceptions;

/** Riot n'a pas répondu, ou a répondu ce qu'on ne sait pas lire. La panne est en amont. */
public class RiotApiException extends RuntimeException {

    public RiotApiException(String message) {
        super(message);
    }

    public RiotApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
