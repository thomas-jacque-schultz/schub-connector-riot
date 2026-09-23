package schultz.thomas.schub.connector.riot.business.stats;

import org.bson.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Un dénominateur nul quand le numérateur manque (partie sans timeline, ligne d'avant la v3) : la ligne ne compte pas.
final class ReferencePipeline {

    private ReferencePipeline() {
    }

    static List<Document> parPartie(List<String> patchs, List<ReferenceMetric> metriques) {
        List<Document> pipeline = new ArrayList<>(base(patchs, metriques, false));
        Document groupe = new Document("_id", new Document("position", "$position").append("tier", "$tier"));
        for (ReferenceMetric metrique : metriques) {
            String k = metrique.key();
            Document valeur = new Document("$cond", Arrays.asList(new Document("$gt", List.of("$d_" + k, 0)),
                    new Document("$divide", List.of("$n_" + k, "$d_" + k)), null));
            groupe.append("q_" + k, percentile(valeur))
                    .append("c_" + k, new Document("$sum", new Document("$cond",
                            List.of(new Document("$gt", List.of("$d_" + k, 0)), 1, 0))));
        }
        pipeline.add(new Document("$group", groupe));
        return pipeline;
    }

    // Rapport des sommes par joueur, puis la répartition de ces moyennes. Palier retenu : le plus récent de la fenêtre.
    static List<Document> parMoyenne(List<String> patchs, List<ReferenceMetric> metriques) {
        List<Document> pipeline = new ArrayList<>(base(patchs, metriques, true));
        pipeline.add(new Document("$sort", new Document("startedAt", 1)));
        Document joueur = new Document("_id", new Document("puuid", "$puuid").append("position", "$position"))
                .append("tier", new Document("$last", "$tier"))
                .append("games", new Document("$sum", 1));
        for (ReferenceMetric metrique : metriques) {
            joueur.append("n_" + metrique.key(), new Document("$sum", "$n_" + metrique.key()))
                    .append("d_" + metrique.key(), new Document("$sum", "$d_" + metrique.key()));
        }
        pipeline.add(new Document("$group", joueur));
        pipeline.add(new Document("$match", new Document("games", new Document("$gte",
                ReferenceService.PARTIES_PAR_JOUEUR))));
        Document groupe = new Document("_id", new Document("position", "$_id.position").append("tier", "$tier"));
        for (ReferenceMetric metrique : metriques) {
            String k = metrique.key();
            Document valeur = new Document("$cond", Arrays.asList(new Document("$gt", List.of("$d_" + k, 0)),
                    new Document("$divide", List.of("$n_" + k, "$d_" + k)), null));
            groupe.append("q_" + k, percentile(valeur))
                    .append("c_" + k, new Document("$sum", new Document("$cond",
                            List.of(new Document("$gt", List.of("$d_" + k, 0)), 1, 0))));
        }
        pipeline.add(new Document("$group", groupe));
        return pipeline;
    }

    // Par joueur et par champion, tous postes confondus : un champion se joue presque toujours à un ou deux postes.
    static List<Document> parChampion(List<String> patchs, List<ReferenceMetric> metriques, int partiesParJoueur) {
        List<Document> pipeline = new ArrayList<>(base(patchs, metriques, true));
        pipeline.add(new Document("$sort", new Document("startedAt", 1)));
        Document joueur = new Document("_id", new Document("puuid", "$puuid").append("championId", "$championId"))
                .append("tier", new Document("$last", "$tier"))
                .append("games", new Document("$sum", 1));
        for (ReferenceMetric metrique : metriques) {
            joueur.append("n_" + metrique.key(), new Document("$sum", "$n_" + metrique.key()))
                    .append("d_" + metrique.key(), new Document("$sum", "$d_" + metrique.key()));
        }
        pipeline.add(new Document("$group", joueur));
        pipeline.add(new Document("$match", new Document("games", new Document("$gte", partiesParJoueur))));
        pipeline.add(new Document("$addFields", new Document("groupe", new Document("$switch", new Document("branches", List.of(
                branche(List.of("IRON", "BRONZE"), "IRON_BRONZE"),
                branche(List.of("SILVER", "GOLD"), "SILVER_GOLD"),
                branche(List.of("PLATINUM", "EMERALD"), "PLATINUM_EMERALD")))
                .append("default", "$tier")))));
        Document groupe = new Document("_id", new Document("championId", "$_id.championId").append("tier", "$groupe"));
        for (ReferenceMetric metrique : metriques) {
            String k = metrique.key();
            Document valeur = new Document("$cond", Arrays.asList(new Document("$gt", List.of("$d_" + k, 0)),
                    new Document("$divide", List.of("$n_" + k, "$d_" + k)), null));
            groupe.append("q_" + k, percentile(valeur))
                    .append("c_" + k, new Document("$sum", new Document("$cond",
                            List.of(new Document("$gt", List.of("$d_" + k, 0)), 1, 0))));
        }
        pipeline.add(new Document("$group", groupe));
        return pipeline;
    }

    private static Document branche(List<String> paliers, String groupe) {
        return new Document("case", new Document("$in", List.of("$tier", paliers))).append("then", groupe);
    }

    // Un camp par ligne, déjà à 15 min : pas de dénominateur autre que « la valeur existe ».
    static List<Document> parCamp(List<String> patchs, List<ReferenceMetric> metriques) {
        Document filtre = new Document("patch", new Document("$in", patchs))
                .append("queueId", new Document("$in", ReferenceService.FILES_CLASSEES))
                .append("tier", new Document("$ne", null));
        Document projection = new Document("tier", 1);
        for (ReferenceMetric metrique : metriques) {
            projection.append("n_" + metrique.key(), metrique.numerator())
                    .append("d_" + metrique.key(), new Document("$cond", List.of(
                            new Document("$isNumber", metrique.numerator()), metrique.denominator(), 0)));
        }
        Document groupe = new Document("_id", new Document("position", "TEAM").append("tier", "$tier"));
        for (ReferenceMetric metrique : metriques) {
            String k = metrique.key();
            Document valeur = new Document("$cond", Arrays.asList(new Document("$gt", List.of("$d_" + k, 0)),
                    new Document("$divide", List.of("$n_" + k, "$d_" + k)), null));
            groupe.append("q_" + k, percentile(valeur))
                    .append("c_" + k, new Document("$sum", new Document("$cond",
                            List.of(new Document("$gt", List.of("$d_" + k, 0)), 1, 0))));
        }
        return List.of(new Document("$match", filtre), new Document("$project", projection),
                new Document("$group", groupe));
    }

    private static List<Document> base(List<String> patchs, List<ReferenceMetric> metriques, boolean moyenne) {
        Document filtre = new Document("patch", new Document("$in", patchs))
                .append("queueId", new Document("$in", ReferenceService.FILES_CLASSEES))
                .append("afk", false)
                .append("durationSeconds", new Document("$gte", ReferenceService.DUREE_MINIMUM))
                .append("position", new Document("$in", ReferenceService.POSTES))
                .append("rank.tier", new Document("$ne", null));
        Document projection = new Document("puuid", 1).append("position", 1).append("startedAt", 1).append("championId", 1)
                .append("tier", new Document("$cond", List.of(
                        new Document("$in", List.of("$rank.tier", List.of("MASTER", "GRANDMASTER", "CHALLENGER"))),
                        "MASTER_PLUS", "$rank.tier")));
        for (ReferenceMetric metrique : metriques) {
            projection.append("n_" + metrique.key(), metrique.numerator())
                    .append("d_" + metrique.key(), new Document("$cond", List.of(
                            new Document("$isNumber", metrique.numerator()),
                            moyenne ? metrique.meanDenominator() : metrique.denominator(), 0)));
        }
        return List.of(new Document("$match", filtre), new Document("$project", projection));
    }

    private static Document percentile(Object valeur) {
        return new Document("$percentile", new Document("input", valeur)
                .append("p", ReferenceService.PERCENTILES)
                .append("method", "approximate"));
    }
}
