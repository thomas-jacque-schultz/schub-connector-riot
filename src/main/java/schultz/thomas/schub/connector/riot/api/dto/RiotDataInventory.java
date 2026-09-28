package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Ce qu'un effacement des données Riot emporterait (documents par collection) et ce qu'il garde.")
public record RiotDataInventory(
        Map<String, Long> purged,
        List<String> kept
) {
}
