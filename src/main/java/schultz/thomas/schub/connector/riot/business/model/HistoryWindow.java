package schultz.thomas.schub.connector.riot.business.model;

public record HistoryWindow(
        int maxGames,
        int maxAgeDays,
        int minGames
) {

    public static final int MAX_GAMES_LIMIT = 1000;
    public static final int MAX_AGE_DAYS_LIMIT = 730;

    public boolean valid() {
        return maxGames >= 1 && maxGames <= MAX_GAMES_LIMIT
                && maxAgeDays >= 1 && maxAgeDays <= MAX_AGE_DAYS_LIMIT
                && minGames >= 0 && minGames <= maxGames;
    }
}
