package schultz.thomas.schub.connector.riot.business.quota;

import java.time.Duration;

import schultz.thomas.schub.connector.riot.config.RiotProperties;

public enum QuotaLane {

    INTERACTIVE,

    BULK;

    public Duration timeout(RiotProperties.Quota quota) {
        return this == INTERACTIVE ? quota.getInteractiveTimeout() : quota.getAcquireTimeout();
    }
}
