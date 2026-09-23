package schultz.thomas.schub.connector.riot.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import schultz.thomas.schub.connector.riot.api.dto.MetricScale;
import schultz.thomas.schub.connector.riot.api.dto.ParticipationBucket;
import schultz.thomas.schub.connector.riot.api.dto.PlayerCoverage;
import schultz.thomas.schub.connector.riot.api.dto.PuuidListRequest;
import schultz.thomas.schub.connector.riot.api.dto.SharedMatches;
import schultz.thomas.schub.connector.riot.api.dto.SharedMatchesQuery;
import schultz.thomas.schub.connector.riot.api.dto.StatsQuery;
import schultz.thomas.schub.connector.riot.api.dto.StatsScope;
import schultz.thomas.schub.connector.riot.business.stats.MetricScaleService;
import schultz.thomas.schub.connector.riot.business.stats.ParticipationStatsService;

import java.util.List;

// POST pour des lectures : la liste de puuids ne tient pas dans une URL et n'a rien à faire dans un journal d'accès.
@RestController
@RequiredArgsConstructor
@RequestMapping("/stats")
@Tag(name = "Statistiques", description = "Agrégats sur les participations déjà collectées.")
public class StatsController {

    private final ParticipationStatsService statsService;
    private final MetricScaleService metricScale;

    @Operation(summary = "Agréger des participations sur un axe",
            description = """
                    Des sommes par joueur et par clé de groupe — champion, poste, file, patch,
                    mois, côté, ou tout confondu. **Aucun appel à Riot** : le calcul porte sur ce
                    qui est déjà collecté.

                    `scope: RIFT` ne garde que la Faille en 5 contre 5 : les indicateurs par
                    minute d'une ARAM ou d'une Arène ne se comparent pas aux autres. Le poste
                    inconnu (modes sans couloirs, remakes) n'est jamais un groupe.

                    Un joueur sans participation n'a simplement aucun groupe dans la réponse —
                    pas une erreur, et `/stats/coverage` dit pourquoi.""")
    @PostMapping("/aggregate")
    public List<ParticipationBucket> aggregate(@Valid @RequestBody StatsQuery query) {
        return statsService.aggregate(query.puuids(), query.groupBy(),
                query.scope() == null ? StatsScope.ALL : query.scope(), query.since());
    }

    @Operation(summary = "Sur quoi portent les chiffres de ces joueurs",
            description = """
                    Combien de parties, sur quelle période, et **est-ce ce joueur-là qu'on
                    collecte**.

                    La distinction n'est pas cosmétique : les participations portent les dix
                    joueurs de chaque partie collectée, donc un compte jamais ingéré a quand même
                    des lignes dès qu'il a croisé quelqu'un qu'on suit. Ses chiffres sont alors
                    vrais mais partiels, et `tracked = false` est la seule chose qui le dise.""")
    @PostMapping("/coverage")
    public List<PlayerCoverage> coverage(@Valid @RequestBody PuuidListRequest request) {
        return statsService.coverage(request.puuids());
    }

    @Operation(summary = "Les parties où plusieurs de ces joueurs se retrouvent",
            description = """
                    Rend les parties où au moins `minimumPlayers` des `puuids` donnés étaient
                    présents, les plus récentes d'abord, **toutes files confondues**.

                    Le connecteur compte des présences ; il ne sait pas ce qu'est une équipe, et
                    le seuil vient donc de l'appelant.

                    Une partie où les joueurs demandés n'étaient pas du même camp est rendue avec
                    `splitSides` et sans résultat commun — la compter en victoire ou en défaite
                    serait faux dans les deux sens.""")
    @PostMapping("/shared-matches")
    public SharedMatches sharedMatches(@Valid @RequestBody SharedMatchesQuery query) {
        return statsService.sharedMatches(
                query.puuids(), query.minimumPlayers(), query.since(), query.limit());
    }

    @Operation(summary = "Bornes des indicateurs pour les graphiques radar",
            description = "Recalculées toutes les heures depuis les participations, sans appel à Riot.")
    @GetMapping("/scale")
    public MetricScale scale() {
        return metricScale.current();
    }
}
