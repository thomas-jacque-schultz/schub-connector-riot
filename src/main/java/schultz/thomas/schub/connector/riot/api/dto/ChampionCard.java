package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Un champion du catalogue, à une version donnée du jeu.
 *
 * @param key     identifiant textuel de Data Dragon ({@code MonkeyKing}), qui n'est pas
 *                toujours le nom affiché ({@code Wukong}).
 * @param id      identifiant numérique, celui qu'on retrouve dans {@code match-v5} et dans les
 *                maîtrises. C'est par lui que tout se recoupe.
 * @param iconUrl URL absolue de l'icône, déjà versionnée. Le front n'a pas à savoir composer
 *                une URL Data Dragon.
 */
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
