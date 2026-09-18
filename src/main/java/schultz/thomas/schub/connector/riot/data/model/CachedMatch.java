package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.MatchDetail;

import java.time.Instant;

/**
 * Politique n°1 — <strong>permanent, jamais redemandé</strong>.
 *
 * <p>Une partie terminée est immuable : la redemander ne peut, par construction, rien apporter.
 * Il n'y a donc ni TTL, ni index d'expiration sur cette collection. C'est elle qui porte
 * l'exigence « ce qui a été pull un jour ne doit pas l'être une deuxième fois ».</p>
 *
 * <p>C'est aussi la seule copie : le cœur ne stocke aucune partie. Deux copies de la même
 * donnée, c'est deux vérités et une divergence garantie.</p>
 *
 * @param detail    le contrat exposé, stocké tel quel (voir le {@code package-info} de
 *                  {@code data} : dans un connecteur, le contrat prime).
 * @param fetchedAt utile au seul diagnostic — il n'entre dans aucune décision de fraîcheur.
 */
@Document("riot_match")
public record CachedMatch(@Id String matchId, MatchDetail detail, Instant fetchedAt) {
}
