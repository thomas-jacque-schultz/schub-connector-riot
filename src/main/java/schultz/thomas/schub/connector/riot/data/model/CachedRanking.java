package schultz.thomas.schub.connector.riot.data.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;

import java.time.Instant;
import java.util.List;

/**
 * Politique n°4 — <strong>TTL court (~1 h)</strong>, la donnée la plus volatile du connecteur.
 *
 * <p>Pas d'index d'expiration Mongo : l'entrée périmée est conservée exprès. Quand Riot ne
 * répond pas, un rang d'il y a deux heures avec sa date de relevé vaut mieux qu'une erreur —
 * l'appelant lit {@code observedAt} et sait ce qu'il affiche.</p>
 */
@Document("riot_ranking")
public record CachedRanking(@Id String puuid, List<RankedStanding> standings, Instant fetchedAt) {
}
