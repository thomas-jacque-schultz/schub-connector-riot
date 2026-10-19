package schultz.thomas.schub.connector.riot.business.stats;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChampionComboTableTest {

    private static Document joueur(String tier) {
        return new Document("c", 1).append("p", "MIDDLE").append("t", tier);
    }

    @Test
    @DisplayName("le palier d'un camp est la moyenne des rangs connus, regroupée comme les référentiels")
    void palierDuCamp() {
        assertThat(ChampionComboTable.groupeDuCamp(List.of(joueur("GOLD"), joueur(null), joueur("PLATINUM"))))
                .isEqualTo("PLATINUM_EMERALD");
        assertThat(ChampionComboTable.groupeDuCamp(List.of(joueur("SILVER")))).isEqualTo("SILVER_GOLD");
        assertThat(ChampionComboTable.groupeDuCamp(List.of(joueur("CHALLENGER"), joueur("MASTER"))))
                .isEqualTo("MASTER_PLUS");
    }

    @Test
    @DisplayName("sans aucun rang connu, le camp n'entre dans aucun palier")
    void campSansRang() {
        assertThat(ChampionComboTable.groupeDuCamp(List.of(joueur(null), joueur(null)))).isNull();
    }

    @Test
    @DisplayName("un 3/3 ne passe pas devant un 15/20")
    void borneDeWilson() {
        assertThat(ChampionComboTable.borneBasse(15, 20)).isGreaterThan(ChampionComboTable.borneBasse(3, 3));
    }
}
