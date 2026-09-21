package schultz.thomas.schub.connector.riot.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class QueueKindTest {

    /**
     * Les identifiants viennent de la liste officielle de Riot (queues.json), relevée le
     * 22-09-2026. Les quatre premiers avaient en plus été constatés sur des comptes EUW.
     */
    @ParameterizedTest
    @CsvSource({
            "400, NORMAL_DRAFT",
            "420, RANKED_SOLO",
            "440, RANKED_FLEX",
            "490, QUICKPLAY",
            "430, NORMAL_BLIND",
            "450, ARAM",
            "480, SWIFTPLAY",
            "700, CLASH",
            "720, ARAM_CLASH",
            "870, COOP_VS_AI",
            "890, COOP_VS_AI",
            "900, ARURF",
            "1010, ARURF",
            "1020, ONE_FOR_ALL",
            "1300, NEXUS_BLITZ",
            "1400, ULTIMATE_SPELLBOOK",
            "1900, URF",
            "2000, TUTORIAL",
            "2300, BRAWL",
            "2400, ARAM_MAYHEM",
            "0, CUSTOM"
    })
    @DisplayName("chaque queueId connu est nommé")
    void nommeLesFilesConnues(int queueId, QueueKind attendu) {
        assertThat(QueueKind.fromQueueId(queueId)).isEqualTo(attendu);
    }

    /** C'est le point du regroupement : l'arène a deux files, Swarm en a quatre. */
    @ParameterizedTest
    @CsvSource({
            "1700, ARENA",
            "1710, ARENA",
            "1810, SWARM",
            "1840, SWARM"
    })
    @DisplayName("plusieurs identifiants peuvent désigner le même mode")
    void repliePlusieursFilesSurUnMode(int queueId, QueueKind attendu) {
        assertThat(QueueKind.fromQueueId(queueId)).isEqualTo(attendu);
    }

    @Test
    @DisplayName("une file inconnue n'est pas une erreur : Riot en ajoute, le connecteur survit")
    void toleLesFilesInconnues() {
        assertThat(QueueKind.fromQueueId(9999)).isEqualTo(QueueKind.OTHER);
        assertThat(QueueKind.fromQueueId(-1)).isEqualTo(QueueKind.OTHER);
    }

    @Test
    @DisplayName("un poste vide devient UNKNOWN plutôt que null")
    void tolereUnPosteVide() {
        assertThat(TeamPosition.fromRiot("")).isEqualTo(TeamPosition.UNKNOWN);
        assertThat(TeamPosition.fromRiot(null)).isEqualTo(TeamPosition.UNKNOWN);
        assertThat(TeamPosition.fromRiot("UTILITY")).isEqualTo(TeamPosition.UTILITY);
        assertThat(TeamPosition.fromRiot("MIDDLE")).isEqualTo(TeamPosition.MIDDLE);
    }
}
