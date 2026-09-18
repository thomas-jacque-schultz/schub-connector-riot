package schultz.thomas.schub.connector.riot.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import schultz.thomas.schub.connector.riot.api.dto.MatchDetail;
import schultz.thomas.schub.connector.riot.api.dto.MatchDetailsResponse;
import schultz.thomas.schub.connector.riot.api.dto.MatchIdsRequest;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotResourceNotFoundException;
import schultz.thomas.schub.connector.riot.business.services.MatchDetailService;

/**
 * Le détail des parties.
 *
 * <p>Le connecteur rend les dix participants et la file. Il ne dit pas si la partie « compte »,
 * ni pour qui : la règle « au moins 4 des 5 membres » est du domaine, pas de la traduction.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/matches")
@Tag(name = "Parties", description = "Détail normalisé des parties terminées.")
public class MatchController {

    private final MatchDetailService matchDetailService;

    @Operation(summary = "Le détail d'une partie",
            description = """
                    Une partie terminée est **immuable** : une fois récupérée, elle n'est plus
                    jamais redemandée à Riot. C'est le cœur de l'exigence « ce qui a été pull un
                    jour ne doit pas l'être une deuxième fois », et c'est la seule copie de cette
                    donnée dans Schub — le cœur ne stocke aucune partie.

                    La timeline (plusieurs mégaoctets) n'est ni récupérée ni conservée.

                    404 si Riot ne connaît pas cette partie.""")
    @GetMapping("/{matchId}")
    public MatchDetail detail(@PathVariable String matchId) {
        return matchDetailService.detail(matchId)
                .orElseThrow(() -> new RiotResourceNotFoundException(
                        "Partie inconnue de Riot : " + matchId));
    }

    @Operation(summary = "Le détail de plusieurs parties",
            description = """
                    Un POST pour une lecture, à contre-courant de l'habitude : cent identifiants
                    de parties ne tiennent pas dans une URL, et le cœur en demande cent d'un coup
                    quand il constitue l'historique d'une équipe.

                    Les parties déjà en cache sont rendues sans aucun appel sortant. Pour les
                    autres, le nombre d'appels à Riot est **borné par requête** : ce qui dépasse
                    revient dans `pending`, et un nouvel appel le prendra. Une réponse partielle
                    annoncée vaut mieux qu'une requête qui pend cinq minutes.

                    `unavailable` liste ce que Riot refuse : partie inexistante ou purgée.""")
    @PostMapping("/by-ids")
    public MatchDetailsResponse details(@Valid @RequestBody MatchIdsRequest request) {
        return matchDetailService.details(request.matchIds());
    }
}
