package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Le résultat d'un camp : victoire, bans, objectifs.")
public record MatchTeamResult(
        int teamId,
        boolean win,
        List<Integer> bannedChampionIds,
        Map<String, Integer> objectives
) {
}
