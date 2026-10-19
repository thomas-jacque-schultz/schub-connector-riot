package schultz.thomas.schub.connector.riot.api.dto;

import java.time.Instant;
import java.util.List;

// Les combos d'un groupe de paliers, du meilleur au moins bon par la borne basse de Wilson (Schub#112).
public record TierCombos(String tier, String kind, Instant computedAt, long games, List<Ranked> combos) {

    // championIds dans l'ordre des postes du type de combo.
    public record Ranked(List<Integer> championIds, long games, long wins, double lowerBound) {
    }
}
