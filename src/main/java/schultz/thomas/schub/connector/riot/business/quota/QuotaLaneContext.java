package schultz.thomas.schub.connector.riot.business.quota;

// Porté par le thread : MatchDetailService et MatchHistoryService servent les deux voies avec le même code.
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
