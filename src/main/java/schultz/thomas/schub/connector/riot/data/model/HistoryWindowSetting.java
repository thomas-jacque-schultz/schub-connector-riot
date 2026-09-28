package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// prunedAt antérieur à updatedAt : la file n'a pas encore été ramenée à cette fenêtre.
@Document("riot_history_window")
public record HistoryWindowSetting(@Id String id, int maxGames, int maxAgeDays, int minGames, Instant updatedAt,
                                   Instant prunedAt) {

    public static final String CURRENT = "current";

    public boolean pruned() {
        return prunedAt != null && (updatedAt == null || !prunedAt.isBefore(updatedAt));
    }

    public HistoryWindowSetting prunedAt(Instant instant) {
        return new HistoryWindowSetting(id, maxGames, maxAgeDays, minGames, updatedAt, instant);
    }
}
