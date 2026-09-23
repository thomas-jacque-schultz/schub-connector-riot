package schultz.thomas.schub.connector.riot.business.mapper;

import org.springframework.stereotype.Component;

import schultz.thomas.schub.connector.riot.api.dto.ChampionCard;
import schultz.thomas.schub.connector.riot.api.dto.ChampionMastery;
import schultz.thomas.schub.connector.riot.api.dto.PlayerIdentity;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;
import schultz.thomas.schub.connector.riot.data.model.riot.DataDragonChampionList;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotAccountResponse;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotChampionMasteryResponse;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotLeagueEntryResponse;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
public class RiotStatsMapper {

    public PlayerIdentity toIdentity(RiotAccountResponse account) {
        return new PlayerIdentity(account.puuid(), account.gameName(), account.tagLine());
    }

    public RankedStanding toStanding(RiotLeagueEntryResponse entry, Instant observedAt) {
        return new RankedStanding(
                queueOf(entry.queueType()),
                entry.queueType(),
                entry.tier(),
                entry.rank(),
                entry.leaguePoints(),
                entry.wins(),
                entry.losses(),
                entry.hotStreak(),
                entry.inactive(),
                observedAt);
    }

    private QueueKind queueOf(String queueType) {
        if (queueType == null) {
            return QueueKind.OTHER;
        }
        return switch (queueType) {
            case "RANKED_SOLO_5x5" -> QueueKind.RANKED_SOLO;
            case "RANKED_FLEX_SR" -> QueueKind.RANKED_FLEX;
            default -> QueueKind.OTHER;
        };
    }

    public ChampionMastery toMastery(RiotChampionMasteryResponse mastery, String championName,
                                     Instant observedAt) {
        return new ChampionMastery(
                mastery.championId(),
                championName,
                mastery.championLevel(),
                mastery.championPoints(),
                mastery.lastPlayTime() > 0 ? Instant.ofEpochMilli(mastery.lastPlayTime()) : null,
                observedAt);
    }

    // Data Dragon : key est l'identifiant numérique en chaîne, id l'identifiant textuel (MonkeyKing pour Wukong).
    public List<ChampionCard> toCatalog(DataDragonChampionList list, String iconBaseUrl, String version) {
        if (list == null || list.data() == null) {
            return List.of();
        }
        return list.data().values().stream()
                .map(champion -> new ChampionCard(
                        champion.id(),
                        parseId(champion.key()),
                        champion.name(),
                        champion.title(),
                        champion.tags() == null ? List.of() : champion.tags(),
                        iconBaseUrl + "/cdn/" + version + "/img/champion/"
                                + (champion.image() == null ? champion.id() + ".png" : champion.image().full())))
                .sorted(Comparator.comparing(ChampionCard::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private int parseId(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException | NullPointerException malformed) {
            return -1;
        }
    }
}
