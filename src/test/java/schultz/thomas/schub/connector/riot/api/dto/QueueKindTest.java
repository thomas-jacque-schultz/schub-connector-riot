package schultz.thomas.schub.connector.riot.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class QueueKindTest {

    /**
     * Les quatre premières valeurs ont été relevées sur l'API réelle le 18-09 : des comptes EUW
     * ont rendu des parties pour chacune. C'est ce que l'équipe joue — flex, classée 5v5 et
     * draft normale — plus la file rapide.
     */
    @ParameterizedTest
    @CsvSource({
            "400, NORMAL_DRAFT",
            "420, RANKED_SOLO",
            "440, RANKED_FLEX",
            "490, QUICKPLAY",
            "430, NORMAL_BLIND",
            "450, ARAM",
            "700, CLASH",
            "0, CUSTOM"
    })
    @DisplayName("chaque queueId connu est nommé")
    void nommeLesFilesConnues(int queueId, QueueKind attendu) {
        assertThat(QueueKind.fromQueueId(queueId)).isEqualTo(attendu);
    }

    @Test
    @DisplayName("une file inconnue n'est pas une erreur : Riot en ajoute, le connecteur survit")
    void toleLesFilesInconnues() {
        assertThat(QueueKind.fromQueueId(1700)).isEqualTo(QueueKind.OTHER);
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
