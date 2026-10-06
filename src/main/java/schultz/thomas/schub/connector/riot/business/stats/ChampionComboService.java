package schultz.thomas.schub.connector.riot.business.stats;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.connector.riot.api.dto.ChampionCombo;
import schultz.thomas.schub.connector.riot.api.dto.ChampionCombosQuery;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Repère général des conseils de draft (Schub#105, #108) : tous joueurs et paliers confondus (réponse du 05/10).
// Un seul passage groupé par (partie, camp), servi par l'index champion_position_partie.
@Service
@RequiredArgsConstructor
public class ChampionComboService {

    static final int EMPLACEMENTS_MAX = 3;

    private final MongoTemplate mongo;
    private final ChampionComboTable table;

    public List<ChampionCombo> combos(ChampionCombosQuery query) {
        List<ChampionCombosQuery.Slot> slots = query.slots();
        if (slots == null || slots.size() < 2 || slots.size() > EMPLACEMENTS_MAX
                || slots.stream().map(ChampionCombosQuery.Slot::position).distinct().count() != slots.size()) {
            throw new IllegalArgumentException("Deux ou trois emplacements, à des postes distincts");
        }
        return table.combos(query).orElseGet(() -> enDirect(query));
    }

    // Repli tant que la table du jour n'est pas calculée : quelques secondes par requête.
    private List<ChampionCombo> enDirect(ChampionCombosQuery query) {
        List<ChampionCombosQuery.Slot> slots = query.slots();
        Set<Integer> champions = new HashSet<>();
        slots.forEach(slot -> champions.addAll(slot.championIds()));
        Aggregation agregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("championId").in(champions)
                        .and("position").in(slots.stream().map(ChampionCombosQuery.Slot::position).toList())),
                Aggregation.group("matchId", "side")
                        .first("win").as("win")
                        .push(new Document("c", "$championId").append("p", "$position")).as("joueurs"))
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build());

        Map<List<Integer>, long[]> comptes = new HashMap<>();
        for (Document camp : mongo.aggregate(agregation, MatchParticipation.COLLECTION, Document.class)) {
            List<Document> joueurs = camp.getList("joueurs", Document.class);
            List<Integer> combo = new ArrayList<>(slots.size());
            for (ChampionCombosQuery.Slot slot : slots) {
                joueurs.stream()
                        .filter(j -> slot.position().name().equals(j.getString("p"))
                                && slot.championIds().contains(j.getInteger("c")))
                        .findFirst()
                        .ifPresent(j -> combo.add(j.getInteger("c")));
            }
            if (combo.size() == slots.size()) {
                long[] compte = comptes.computeIfAbsent(combo, k -> new long[2]);
                compte[0]++;
                compte[1] += Boolean.TRUE.equals(camp.getBoolean("win")) ? 1 : 0;
            }
        }
        return comptes.entrySet().stream()
                .filter(e -> e.getValue()[0] >= Math.max(1, query.minGames()))
                .map(e -> new ChampionCombo(List.copyOf(e.getKey()), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(ChampionCombo::games).reversed())
                .toList();
    }
}
