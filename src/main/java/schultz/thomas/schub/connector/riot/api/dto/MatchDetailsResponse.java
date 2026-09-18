package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Réponse à une demande de détails en lot.
 *
 * <p>Les parties absentes ne sont pas une erreur : le connecteur borne volontairement le nombre
 * d'appels sortants par requête entrante. {@code pending} dit lesquelles restent à récupérer,
 * et un nouvel appel les prendra. Une réponse partielle annoncée vaut mieux qu'une requête qui
 * pend cinq minutes ou qu'un trou silencieux.</p>
 */
@Schema(description = "Détails de parties, éventuellement partiels.")
public record MatchDetailsResponse(
        List<MatchDetail> matches,
        @Schema(description = "Ids dont le détail reste à récupérer : rappeler pour les obtenir.")
        List<String> pending,
        @Schema(description = "Ids refusés par Riot (partie inexistante ou purgée).")
        List<String> unavailable
) {
}
