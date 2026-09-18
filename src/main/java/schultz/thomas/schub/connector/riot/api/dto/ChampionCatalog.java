package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Le catalogue des champions, <strong>figé à une version</strong>.
 *
 * <p>C'est la nature de cette donnée qui commande sa politique de cache : elle est immuable par
 * version, donc gardée pour toujours avec la version pour clé. C'est ce qui permet de rouvrir
 * une composition préparée en mars et de la voir juste, au lieu d'y lire des champions
 * rééquilibrés depuis — ou qui n'existaient pas.</p>
 *
 * @param version version Data Dragon, toujours renvoyée, y compris quand l'appelant ne l'a pas
 *                demandée : une composition doit pouvoir enregistrer laquelle elle a utilisée.
 */
@Schema(description = "Catalogue des champions figé à une version Data Dragon.")
public record ChampionCatalog(
        @Schema(example = "16.18.1") String version,
        @Schema(example = "fr_FR") String locale,
        List<ChampionCard> champions
) {
}
