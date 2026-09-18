package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * La maîtrise d'un champion par un joueur.
 *
 * <p>Évolue en jouant, donc TTL de six heures : un pool de champions n'a pas besoin d'être à la
 * seconde, et le rafraîchir en continu brûlerait du quota pour un chiffre que personne ne
 * regarde changer.</p>
 *
 * <p>Le connecteur rend la maîtrise, pas le « pool jouable par poste » : décider qu'un joueur
 * peut tenir un poste est un jugement de domaine.</p>
 *
 * @param championName renseigné quand le catalogue Data Dragon a pu être croisé, {@code null}
 *                     sinon. L'identifiant numérique, lui, est toujours là.
 */
@Schema(description = "Maîtrise d'un champion par un joueur.")
public record ChampionMastery(
        int championId,
        String championName,
        int level,
        int points,
        Instant lastPlayedAt,
        Instant observedAt
) {
}
