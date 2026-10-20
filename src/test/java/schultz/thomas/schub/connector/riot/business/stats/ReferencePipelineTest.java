package schultz.thomas.schub.connector.riot.business.stats;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReferencePipelineTest {

    @Test
    @DisplayName("les valeurs d'une partie gardent les dénominateurs de la grille GAME, et seulement ses métriques")
    void valeursDePartie() {
        List<Document> pipeline = ReferencePipeline.valeursDePartie("EUW1_1", MatchMetricsService.PAR_PARTIE);

        assertThat(pipeline.getFirst().toJson()).contains("\"matchId\": \"EUW1_1\"");
        Document termes = pipeline.get(1).get("$project", Document.class);
        assertThat(termes.get("d_kda", Document.class).toJson()).contains("$max");
        Document valeurs = pipeline.get(2).get("$project", Document.class);
        assertThat(valeurs).containsKeys("csPerMinute", "goldDiffAt15", "puuid", "rank").doesNotContainKey("winRate");
    }

    @Test
    @DisplayName("la répartition compte les parties par poste et par case, la dernière case bornée (Schub#129)")
    void repartitions() {
        List<Document> pipeline = ReferencePipeline.repartitions(List.of("16.19"), DistributionMetric.CATALOGUE);

        assertThat(pipeline.getFirst().get("$match", Document.class)).containsKeys("patch", "queueId", "position");
        Document facettes = pipeline.get(1).get("$facet", Document.class);
        assertThat(facettes).containsOnlyKeys("kills", "deaths", "kda");
        assertThat(facettes.toJson()).contains("\"$min\"", "20");
    }
}
