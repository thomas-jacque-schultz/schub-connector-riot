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

/** Traduit l'identité, les classements, les maîtrises et le catalogue en vocabulaire de domaine. */
@Component
public class RiotStatsMapper {

    public PlayerIdentity toIdentity(RiotAccountResponse account) {
        return new PlayerIdentity(account.puuid(), account.gameName(), account.tagLine());
    }

    /**
     * {@code queueType} de Riot ({@code RANKED_SOLO_5x5}) est conservé à côté de sa lecture :
     * c'est la donnée, le reste est une interprétation.
     */
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

    /**
     * Le catalogue, trié par nom.
     *
     * <p>{@code key} de Data Dragon est l'identifiant numérique rendu en chaîne, et {@code id}
     * son identifiant textuel — l'inverse de ce que les deux noms laissent croire. C'est le
     * genre d'inversion qu'on ne voit pas en relisant.</p>
     */
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
