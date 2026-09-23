package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// Première page vide connue d'une division : le tirage des pages reste en deçà.
@Document("riot_ladder_bound")
public record LadderBound(@Id String division, int firstEmptyPage) {
}
