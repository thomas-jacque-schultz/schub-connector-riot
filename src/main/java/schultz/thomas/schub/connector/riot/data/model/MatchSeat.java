package schultz.thomas.schub.connector.riot.data.model;

import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;

// Qui jouait quoi dans une partie : une ligne de riot_participation réduite à la place du joueur.
public record MatchSeat(String puuid, String matchId, int side, TeamPosition position, int championId) {
}
