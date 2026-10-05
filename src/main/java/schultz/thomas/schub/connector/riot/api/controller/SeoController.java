package schultz.thomas.schub.connector.riot.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.connector.riot.api.dto.SeoCreators;
import schultz.thomas.schub.connector.riot.business.seo.SeoCreatorService;

import java.util.List;

@RestController
@RequestMapping("/seo")
@RequiredArgsConstructor
@Tag(name = "Référencement", description = "Ce que le plan du site de PremadeLab met en avant.")
public class SeoController {

    private final SeoCreatorService creators;

    @Operation(summary = "Les créateurs de contenu suivis")
    @GetMapping("/creators")
    public SeoCreators creators() {
        return creators.current();
    }

    @Operation(summary = "Remplacer la liste des créateurs de contenu",
            description = "Chaque Riot ID est résolu chez Riot ; un nouveau venu voit son historique relevé. "
                    + "Les Riot ID inconnus sont rendus dans `unknown` et laissés hors de la liste.")
    @PutMapping("/creators")
    public SeoCreators replace(@RequestBody List<String> riotIds) {
        return creators.replace(riotIds);
    }
}
