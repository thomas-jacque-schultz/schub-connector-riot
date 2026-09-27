package schultz.thomas.schub.connector.riot.business.services;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.connector.riot.business.client.DataDragonClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Les items d'une version, tels que les règles de build les lisent (R&D Schub#19). Data Dragon ne structure
 * pas les blessures graves : elles se lisent dans le texte, et une petite liste tenue à la main les confirme.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemCatalogService {

    private static final Pattern BLESSURES = Pattern.compile("Grievous\\s*Wounds", Pattern.CASE_INSENSITIVE);
    // Exécuteur, Rappel mortel, Morellonomicon, Orbe de l'oubli, Cotte épineuse, Veste de ronces,
    // Épée-scie chimtech, Putréfacteur chimtech : relus au patch 16.19.
    private static final Set<Integer> BLESSURES_CONNUES = Set.of(3123, 3033, 3165, 3916, 3075, 3076, 6609, 3011);
    private static final int ITEM_COMPLET = 2500;
    // Présage de Randuin (réduit les critiques subis) et Tabi ninja (réduit les attaques de base).
    public static final Set<Integer> ANTI_CRITIQUE = Set.of(3143, 3047);

    public record ItemCatalog(String version, Set<Integer> grievous, Set<Integer> critical,
                              Map<Integer, Double> armor, Map<Integer, Double> magicResist) {
    }

    private final DataDragonClient dataDragon;
    private final ChampionCatalogService champions;
    private final Map<String, ItemCatalog> parVersion = new ConcurrentHashMap<>();

    public Optional<ItemCatalog> current() {
        try {
            String version = champions.currentVersion();
            return Optional.of(parVersion.computeIfAbsent(version, this::charge));
        } catch (RuntimeException e) {
            log.warn("Catalogue des items indisponible : {}", e.getMessage());
            return Optional.empty();
        }
    }

    private ItemCatalog charge(String version) {
        JsonNode data = dataDragon.items(version, "en_US").path("data");
        Set<Integer> blessures = new HashSet<>(BLESSURES_CONNUES);
        Set<Integer> critique = new HashSet<>();
        Map<Integer, Double> armure = new HashMap<>();
        Map<Integer, Double> resistance = new HashMap<>();
        data.fields().forEachRemaining(entree -> {
            int id;
            try {
                id = Integer.parseInt(entree.getKey());
            } catch (NumberFormatException autre) {
                return;
            }
            JsonNode item = entree.getValue();
            if (BLESSURES.matcher(item.path("description").asText("")).find()) {
                blessures.add(id);
            }
            boolean crit = false;
            for (JsonNode tag : item.path("tags")) {
                crit |= "CriticalStrike".equals(tag.asText());
            }
            if (crit && item.path("gold").path("total").asInt() >= ITEM_COMPLET) {
                critique.add(id);
            }
            armure.put(id, item.path("stats").path("FlatArmorMod").asDouble(0));
            resistance.put(id, item.path("stats").path("FlatSpellBlockMod").asDouble(0));
        });
        log.info("Catalogue des items {} : {} à blessures graves, {} de critique.", version, blessures.size(), critique.size());
        return new ItemCatalog(version, blessures, critique, armure, resistance);
    }
}
