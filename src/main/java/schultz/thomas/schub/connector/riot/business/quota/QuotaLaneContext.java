package schultz.thomas.schub.connector.riot.business.quota;

/**
 * La voie du thread courant.
 *
 * <p>Portée par le thread plutôt que par paramètre : {@code MatchDetailService} et
 * {@code MatchHistoryService} servent les deux voies avec le même code, et seul l'appelant
 * sait laquelle.</p>
 */
public final class QuotaLaneContext {

    private static final ThreadLocal<QuotaLane> CURRENT = ThreadLocal.withInitial(() -> QuotaLane.INTERACTIVE);

    private QuotaLaneContext() {
    }

    public static QuotaLane current() {
        return CURRENT.get();
    }

    public static void runAsBulk(Runnable work) {
        QuotaLane previous = CURRENT.get();
        CURRENT.set(QuotaLane.BULK);
        try {
            work.run();
        } finally {
            CURRENT.set(previous);
        }
    }
}
