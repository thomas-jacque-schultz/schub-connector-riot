package schultz.thomas.schub.connector.riot.data.model.riot;

import java.util.List;

// Ligues Maître, GM et Challenger : les entrées ne portent ni le palier ni la file, la liste si.
public record RiotLeagueListResponse(String tier, String queue, List<RiotLeagueEntryResponse> entries) {
}
