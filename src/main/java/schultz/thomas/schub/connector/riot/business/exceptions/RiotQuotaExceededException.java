package schultz.thomas.schub.connector.riot.business.exceptions;

import java.time.Duration;

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
