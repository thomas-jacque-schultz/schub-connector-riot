package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Le résultat d'un des deux camps.
 *
 * @param bannedChampionIds les bans, dans l'ordre où Riot les rend. Un ban peut valoir -1 :
 *                          personne n'a banni à ce tour.
 * @param objectives        objectif → nombre de prises. Une {@code Map} et non des champs
 *                          nommés : Riot en ajoute (« atakhan » est apparu en 2025), et un
 *                          champ manquant se lirait comme un bug de désérialisation.
 */
@Schema(description = "Le résultat d'un camp : victoire, bans, objectifs.")
public record MatchTeamResult(
        int teamId,
        boolean win,
        List<Integer> bannedChampionIds,
        Map<String, Integer> objectives
) {
}
