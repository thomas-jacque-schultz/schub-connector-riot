package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Un champion, figé à une version du jeu.")
public record ChampionCard(
        @Schema(example = "Jax") String key,
        @Schema(example = "24") int id,
        @Schema(example = "Jax") String name,
        @Schema(example = "Maître d'armes") String title,
        List<String> tags,
        String iconUrl
) {
}
