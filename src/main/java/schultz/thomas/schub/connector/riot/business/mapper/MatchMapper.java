package schultz.thomas.schub.connector.riot.business.mapper;

import org.springframework.stereotype.Component;

import schultz.thomas.schub.connector.riot.api.dto.MatchDetail;
import schultz.thomas.schub.connector.riot.api.dto.MatchParticipant;
import schultz.thomas.schub.connector.riot.api.dto.MatchTeamResult;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotMatchResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Traduit une partie Riot en partie de domaine.
 *
 * <p>C'est ici, et seulement ici, que le vocabulaire Riot s'arrête. Le connecteur ne juge pas :
 * il ne dit pas si la partie « compte », ni pour qui. Il rend les dix participants et la file,
 * le cœur croise.</p>
 */
@Component
public class MatchMapper {

    private static final String GAME_COMPLETE = "GameComplete";

    public MatchDetail toDomain(RiotMatchResponse response) {
        RiotMatchResponse.Info info = response.info();
        Instant startedAt = Instant.ofEpochMilli(info.gameStartTimestamp());
        long durationSeconds = durationSeconds(info);

        return new MatchDetail(
                response.metadata().matchId(),
                info.queueId(),
                QueueKind.fromQueueId(info.queueId()),
                info.gameVersion(),
                info.platformId(),
                startedAt,
                endedAt(info, startedAt, durationSeconds),
                durationSeconds,
                GAME_COMPLETE.equals(info.endOfGameResult()),
                toParticipants(info.participants()),
                toTeams(info.teams()));
    }

    /**
     * {@code gameDuration} a changé d'unité au patch 11.20 : secondes depuis, millisecondes
     * avant. {@code gameEndTimestamp} est le marqueur qui tranche — il n'existe que dans le
     * cas « secondes ». Sans cette distinction, une partie de 2021 durerait 1 500 000 secondes,
     * ce qui ne lève aucune erreur et fausse toute moyenne.
     */
    private long durationSeconds(RiotMatchResponse.Info info) {
        return info.gameEndTimestamp() > 0 ? info.gameDuration() : info.gameDuration() / 1000;
    }

    private Instant endedAt(RiotMatchResponse.Info info, Instant startedAt, long durationSeconds) {
        return info.gameEndTimestamp() > 0
                ? Instant.ofEpochMilli(info.gameEndTimestamp())
                : startedAt.plusSeconds(durationSeconds);
    }

    private List<MatchParticipant> toParticipants(List<RiotMatchResponse.Participant> participants) {
        if (participants == null) {
            return List.of();
        }
        return participants.stream().map(this::toParticipant).toList();
    }

    private MatchParticipant toParticipant(RiotMatchResponse.Participant participant) {
        return new MatchParticipant(
                participant.puuid(),
                participant.riotIdGameName(),
                participant.riotIdTagline(),
                participant.championId(),
                participant.championName(),
                position(participant),
                participant.teamId(),
                participant.win(),
                participant.kills(),
                participant.deaths(),
                participant.assists(),
                participant.champLevel(),
                participant.totalMinionsKilled() + participant.neutralMinionsKilled(),
                participant.goldEarned(),
                participant.totalDamageDealtToChampions(),
                participant.visionScore(),
                participant.summoner1Id(),
                participant.summoner2Id(),
                participant.items(),
                participant.wasAfk());
    }

    /**
     * {@code teamPosition} est la donnée la plus fiable, mais elle est vide quand Riot n'a pas
     * su trancher. {@code individualPosition} prend alors le relais ; à défaut, le poste est
     * inconnu — et il vaut mieux le dire que d'en inventer un.
     */
    private TeamPosition position(RiotMatchResponse.Participant participant) {
        TeamPosition position = TeamPosition.fromRiot(participant.teamPosition());
        return position == TeamPosition.UNKNOWN
                ? TeamPosition.fromRiot(participant.individualPosition())
                : position;
    }

    private List<MatchTeamResult> toTeams(List<RiotMatchResponse.Team> teams) {
        if (teams == null) {
            return List.of();
        }
        return teams.stream().map(this::toTeam).toList();
    }

    private MatchTeamResult toTeam(RiotMatchResponse.Team team) {
        List<Integer> bans = team.bans() == null
                ? List.of()
                : team.bans().stream().map(RiotMatchResponse.Ban::championId).toList();

        Map<String, Integer> objectives = team.objectives() == null
                ? Map.of()
                : team.objectives().entrySet().stream()
                        .filter(entry -> entry.getValue() != null)
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                entry -> entry.getValue().kills(),
                                (first, second) -> first));

        return new MatchTeamResult(team.teamId(), team.win(), bans, objectives);
    }

    /** Index puuid → participant, pour éviter un balayage par joueur côté appelant. */
    public Map<String, MatchParticipant> byPuuid(MatchDetail match) {
        return match.participants().stream()
                .collect(Collectors.toMap(MatchParticipant::puuid, Function.identity(),
                        (first, second) -> first));
    }
}
