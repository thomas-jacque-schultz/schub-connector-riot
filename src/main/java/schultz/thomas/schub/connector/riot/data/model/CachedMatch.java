package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;

import java.time.Instant;

/**
 * Politique n°1 — permanent, jamais redemandé.
 *
 * <p>Le JSON de Riot est stocké <strong>intégral</strong>, et non la forme normalisée : Riot ne
 * garde qu'environ mille parties par joueur (mesuré, voir la PR), donc ce qui n'est pas capté
 * ici est perdu pour toujours. Toute couche dérivée se recalcule depuis ce champ, sans appel.</p>
 */
@org.springframework.data.mongodb.core.mapping.Document("riot_match")
public record CachedMatch(@Id String matchId, org.bson.Document raw, Instant fetchedAt) {
}
