package schultz.thomas.schub.connector.riot.business.model;

public record IngestSummary(Counts matches, Counts profiles) {

    public record Counts(long retrieved, long analysed, long pending) {
    }
}
