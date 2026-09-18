package schultz.thomas.schub.connector.riot.business.exceptions;

import java.time.Duration;

/**
 * Le quota Riot est épuisé et l'attente dépasserait ce qu'on accepte de faire patienter
 * l'appelant.
 *
 * <p>Distincte d'une panne : rien n'est cassé, il faut revenir plus tard. L'appelant reçoit un
 * 429 et un {@code Retry-After}, exactement comme Riot nous l'a servi — mentir sur la nature de
 * l'échec ferait réessayer immédiatement, ce qui empirerait la situation.</p>
 */
public class RiotQuotaExceededException extends RuntimeException {

    private final transient Duration retryAfter;

    public RiotQuotaExceededException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
