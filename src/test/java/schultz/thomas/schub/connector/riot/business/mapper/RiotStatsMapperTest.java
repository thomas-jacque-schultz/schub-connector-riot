package schultz.thomas.schub.connector.riot.business.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import schultz.thomas.schub.connector.riot.api.dto.ChampionCard;
import schultz.thomas.schub.connector.riot.api.dto.ChampionMastery;
import schultz.thomas.schub.connector.riot.api.dto.QueueKind;
import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;
import schultz.thomas.schub.connector.riot.data.model.riot.DataDragonChampionList;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotChampionMasteryResponse;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotLeagueEntryResponse;
import schultz.thomas.schub.connector.riot.support.Fixtures;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiotStatsMapperTest {

    private static final Instant RELEVE = Instant.parse("2026-09-18T18:00:00Z");

    private final RiotStatsMapper mapper = new RiotStatsMapper();

    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    @DisplayName("une entrée league-v4 réelle devient un classement daté")
    void traduitUnClassementReel() throws Exception {
        List<RiotLeagueEntryResponse> entries = json.readValue(
                Fixtures.raw("league-entries.json"), new TypeReference<>() { });

        RankedStanding standing = mapper.toStanding(entries.get(0), RELEVE);

        assertThat(standing.queue()).isEqualTo(QueueKind.RANKED_SOLO);
        assertThat(standing.riotQueueType()).isEqualTo("RANKED_SOLO_5x5");
        assertThat(standing.tier()).isEqualTo("CHALLENGER");
        assertThat(standing.leaguePoints()).isEqualTo(4659);
        assertThat(standing.observedAt()).isEqualTo(RELEVE);
    }

    @Test
    @DisplayName("une maîtrise réelle est traduite, horodatage compris")
    void traduitUneMaitriseReelle() throws Exception {
        List<RiotChampionMasteryResponse> masteries = json.readValue(
                Fixtures.raw("champion-mastery.json"), new TypeReference<>() { });

        ChampionMastery mastery = mapper.toMastery(masteries.get(0), "Jax", RELEVE);

        assertThat(mastery.championId()).isEqualTo(126);
        assertThat(mastery.points()).isEqualTo(468082);
        assertThat(mastery.lastPlayedAt()).isEqualTo(Instant.ofEpochMilli(1789682346000L));
        assertThat(mastery.observedAt()).isEqualTo(RELEVE);
    }

    @Test
    @DisplayName("Data Dragon nomme « key » l'identifiant numérique et « id » le textuel")
    void demeleLInversionDeDataDragon() {
        DataDragonChampionList list =
                Fixtures.load("ddragon-champions.json", DataDragonChampionList.class);

        List<ChampionCard> catalogue = mapper.toCatalog(list, "https://ddragon.example", "16.18.1");

        assertThat(catalogue).extracting(ChampionCard::name)
                .containsExactly("Aatrox", "Jax", "Wukong");

        ChampionCard wukong = catalogue.stream()
                .filter(card -> "Wukong".equals(card.name())).findFirst().orElseThrow();
        assertThat(wukong.key()).isEqualTo("MonkeyKing");
        assertThat(wukong.id()).isEqualTo(62);
        assertThat(wukong.iconUrl())
                .isEqualTo("https://ddragon.example/cdn/16.18.1/img/champion/MonkeyKing.png");
    }
}
