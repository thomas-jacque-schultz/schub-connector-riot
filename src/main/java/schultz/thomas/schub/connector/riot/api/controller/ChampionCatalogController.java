package schultz.thomas.schub.connector.riot.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import schultz.thomas.schub.connector.riot.api.dto.ChampionCatalog;
import schultz.thomas.schub.connector.riot.business.services.ChampionCatalogService;

import java.util.Map;

/** Le catalogue des champions, figé à une version du jeu. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Champions", description = "Catalogue Data Dragon, figé par version.")
public class ChampionCatalogController {

    private final ChampionCatalogService catalogService;

    @Operation(summary = "Le catalogue des champions",
            description = """
                    Immuable **par version**, donc gardé pour toujours avec la version pour clé.
                    C'est ce qui permet de rouvrir une composition préparée en mars et de la voir
                    juste, au lieu d'y lire des champions rééquilibrés depuis — ou qui
                    n'existaient pas.

                    La version est **toujours rendue**, y compris quand l'appelant ne l'a pas
                    demandée : une composition doit pouvoir enregistrer celle qu'elle a utilisée.

                    Les URL d'icônes sont absolues et déjà versionnées : le front n'a pas à
                    savoir composer une URL Data Dragon.""")
    @GetMapping("/champions")
    public ChampionCatalog champions(
            @Parameter(description = "Version Data Dragon. Absente = la version courante.",
                    example = "16.18.1")
            @RequestParam(required = false) String version) {
        return catalogService.catalog(version);
    }

    @Operation(summary = "La version courante du jeu",
            description = "La seule part mouvante de Data Dragon, et donc la seule à porter un "
                    + "TTL. À figer au moment où une composition est enregistrée.")
    @GetMapping("/game-version")
    public Map<String, String> gameVersion() {
        return Map.of("version", catalogService.currentVersion());
    }
}
