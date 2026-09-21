package schultz.thomas.schub.connector.riot.business.quota;

import java.time.Duration;

import schultz.thomas.schub.connector.riot.config.RiotProperties;

/** Les deux voies d'accès au quota. Un seul compteur, deux politiques d'attente. */
public enum QuotaLane {

    /** Un humain attend au bout : on sert vite, ou on renonce vite. */
    INTERACTIVE,

    /** L'ingestion de masse : elle a tout son temps et cède le passage. */
    BULK;

    public Duration timeout(RiotProperties.Quota quota) {
        return this == INTERACTIVE ? quota.getInteractiveTimeout() : quota.getAcquireTimeout();
    }
}
