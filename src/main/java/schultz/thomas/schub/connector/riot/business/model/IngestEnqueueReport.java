package schultz.thomas.schub.connector.riot.business.model;

public record IngestEnqueueReport(String puuid, boolean queued, IngestStatus status) {
}
