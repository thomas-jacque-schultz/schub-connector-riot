package schultz.thomas.schub.connector.riot.business.quota;

import java.time.Duration;

import schultz.thomas.schub.connector.riot.config.RiotProperties;

public enum QuotaLane {

    INTERACTIVE,

    // Tâche de file demandée par un joueur (aperçu d'un joueur recherché) : attend comme BULK, mais puise dans
    // la réserve interactive et la collecte de fond lui cède la place.
    PRIORITY,

    BULK;

    public Duration timeout(RiotProperties.Quota quota) {
        return this == INTERACTIVE ? quota.getInteractiveTimeout() : quota.getAcquireTimeout();
    }
}
