package schultz.thomas.schub.connector.riot.business.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.connector.riot.api.dto.ChampionCombo;
import schultz.thomas.schub.connector.riot.api.dto.ChampionCombosQuery;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

// Le repère général change à peine d'un jour à l'autre : tous les duos par paire de postes, et le trio
// jungle/milieu/support, comptés une fois par jour. Une requête n'est plus qu'une lecture (18 s → instantané).
@Slf4j
@Service
@RequiredArgsConstructor
public class ChampionComboTable {

    static final List<TeamPosition> POSTES = List.of(TeamPosition.TOP, TeamPosition.JUNGLE, TeamPosition.MIDDLE,
            TeamPosition.BOTTOM, TeamPosition.UTILITY);
    static final List<TeamPosition> TRIO = List.of(TeamPosition.JUNGLE, TeamPosition.MIDDLE, TeamPosition.UTILITY);
    // Un identifiant de champion tient sur 12 bits, un poste sur 3.
    private static final int BITS = 15;

    private final MongoTemplate mongo;

    private volatile Map<Long, long[]> table;

    public void refresh() {
        long debut = System.currentTimeMillis();
        Map<Long, long[]> comptes = new HashMap<>();
        Aggregation agregation = Aggregation.newAggregation(
                        Aggregation.match(Criteria.where("position").in(POSTES)),
                        Aggregation.group("matchId", "side")
                                .first("win").as("win")
                                .push(new Document("c", "$championId").append("p", "$position")).as("joueurs"))
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build());
        try (Stream<Document> camps = mongo.aggregateStream(agregation, MatchParticipation.COLLECTION, Document.class)) {
            camps.forEach(camp -> {
                int[] parPoste = new int[POSTES.size()];
                Arrays.fill(parPoste, -1);
                for (Document joueur : camp.getList("joueurs", Document.class)) {
                    int poste = POSTES.indexOf(TeamPosition.valueOf(joueur.getString("p")));
                    parPoste[poste] = joueur.getInteger("c");
                }
                boolean victoire = Boolean.TRUE.equals(camp.getBoolean("win"));
                for (int i = 0; i < parPoste.length; i++) {
                    for (int j = i + 1; j < parPoste.length; j++) {
                        if (parPoste[i] >= 0 && parPoste[j] >= 0) {
                            compte(comptes, cle(new int[]{i, j}, new int[]{parPoste[i], parPoste[j]}), victoire);
                        }
                    }
                }
                int[] trio = TRIO.stream().mapToInt(POSTES::indexOf).toArray();
                if (Arrays.stream(trio).allMatch(p -> parPoste[p] >= 0)) {
                    compte(comptes, cle(trio, Arrays.stream(trio).map(p -> parPoste[p]).toArray()), victoire);
                }
            });
        }
        table = comptes;
        log.info("Combos de champions : {} entrées comptées en {} s", comptes.size(),
                (System.currentTimeMillis() - debut) / 1000);
    }

    private static void compte(Map<Long, long[]> comptes, long cle, boolean victoire) {
        long[] compte = comptes.computeIfAbsent(cle, k -> new long[2]);
        compte[0]++;
        compte[1] += victoire ? 1 : 0;
    }

    // Postes dans l'ordre de POSTES : la clé ne dépend pas de l'ordre des emplacements de la requête.
    private static long cle(int[] postes, int[] champions) {
        long cle = postes.length;
        for (int i = 0; i < postes.length; i++) {
            cle = (cle << BITS) | ((long) postes[i] << 12) | champions[i];
        }
        return cle;
    }

    // Vide tant que la table n'est pas calculée, ou pour un trio autre que jungle/milieu/support.
    public Optional<List<ChampionCombo>> combos(ChampionCombosQuery query) {
        Map<Long, long[]> courante = table;
        List<ChampionCombosQuery.Slot> slots = query.slots();
        List<TeamPosition> postes = slots.stream().map(ChampionCombosQuery.Slot::position).toList();
        if (courante == null || (slots.size() == 3 && !postes.containsAll(TRIO))) {
            return Optional.empty();
        }
        Integer[] ordre = new Integer[slots.size()];
        for (int i = 0; i < ordre.length; i++) {
            ordre[i] = i;
        }
        Arrays.sort(ordre, Comparator.comparingInt(i -> POSTES.indexOf(postes.get(i))));
        int[] indicesPostes = Arrays.stream(ordre).mapToInt(i -> POSTES.indexOf(postes.get(i))).toArray();

        List<ChampionCombo> trouves = new ArrayList<>();
        produit(slots, 0, new int[slots.size()], combinaison -> {
            int[] champions = Arrays.stream(ordre).mapToInt(i -> combinaison[i]).toArray();
            long[] compte = courante.get(cle(indicesPostes, champions));
            if (compte != null && compte[0] >= Math.max(1, query.minGames())) {
                trouves.add(new ChampionCombo(Arrays.stream(combinaison).boxed().toList(), compte[0], compte[1]));
            }
        });
        trouves.sort(Comparator.comparingLong(ChampionCombo::games).reversed());
        return Optional.of(trouves);
    }

    private static void produit(List<ChampionCombosQuery.Slot> slots, int i, int[] courant,
                                java.util.function.Consumer<int[]> visite) {
        if (i == slots.size()) {
            visite.accept(courant.clone());
            return;
        }
        for (int champion : slots.get(i).championIds()) {
            courant[i] = champion;
            produit(slots, i + 1, courant, visite);
        }
    }
}
