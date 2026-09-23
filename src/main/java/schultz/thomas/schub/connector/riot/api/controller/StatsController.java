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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import schultz.thomas.schub.connector.riot.api.dto.ChampionReferenceGrid;
import schultz.thomas.schub.connector.riot.api.dto.MatchIdsRequest;
import schultz.thomas.schub.connector.riot.api.dto.MatchInsights;
import schultz.thomas.schub.connector.riot.api.dto.ParticipationBucket;
import schultz.thomas.schub.connector.riot.api.dto.PatchStart;
import schultz.thomas.schub.connector.riot.api.dto.PlayerCoverage;
import schultz.thomas.schub.connector.riot.api.dto.PlayerReferences;
import schultz.thomas.schub.connector.riot.api.dto.PuuidListRequest;
import schultz.thomas.schub.connector.riot.api.dto.ReferenceGrid;
import schultz.thomas.schub.connector.riot.api.dto.ReferencesQuery;
import schultz.thomas.schub.connector.riot.api.dto.SharedMatch;
import schultz.thomas.schub.connector.riot.api.dto.SharedMatches;
import schultz.thomas.schub.connector.riot.api.dto.SharedMatchesQuery;
import schultz.thomas.schub.connector.riot.api.dto.StatsQuery;
import schultz.thomas.schub.connector.riot.api.dto.StatsScope;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotResourceNotFoundException;
import schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService;
import schultz.thomas.schub.connector.riot.business.stats.MetricScaleService;
import schultz.thomas.schub.connector.riot.business.stats.ParticipationStatsService;
import schultz.thomas.schub.connector.riot.business.stats.PatchCalendar;
import schultz.thomas.schub.connector.riot.business.stats.ReferenceService;

import java.util.List;

// POST pour des lectures : la liste de puuids ne tient pas dans une URL et n'a rien à faire dans un journal d'accès.
@RestController
@RequiredArgsConstructor
@RequestMapping("/stats")
@Tag(name = "Statistiques", description = "Agrégats sur les participations déjà collectées.")
public class StatsController {

    private final ParticipationStatsService statsService;
    private final MetricScaleService metricScale;
    private final MatchEnrichmentService enrichment;
    private final ReferenceService references;
    private final PatchCalendar patches;

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
        SharedMatches communes = statsService.sharedMatches(
                query.puuids(), query.minimumPlayers(), query.since(), query.limit());
        if (Boolean.TRUE.equals(query.enrich())) {
            enrichment.enqueueMissing(communes.matches().stream().map(SharedMatch::matchId).toList());
        }
        return communes;
    }

    @Operation(summary = "Rangs relevés et chiffres à 15 minutes de parties déjà collectées",
            description = "Aucun appel à Riot : ce qui manque est rendu absent, et se collecte via `enrich`.")
    @PostMapping("/match-insights")
    public List<MatchInsights> matchInsights(@Valid @RequestBody MatchIdsRequest request) {
        return enrichment.insights(request.matchIds());
    }

    @Operation(summary = "Les derniers patchs et la date de leur première partie collectée",
            description = "Le plus récent d'abord. Sert à traduire « les 2 derniers patchs » en date de début.")
    @GetMapping("/patches")
    public List<PatchStart> patches(@RequestParam(defaultValue = "4") int count) {
        return patches.recent(Math.clamp(count, 1, 20));
    }

    @Operation(summary = "Répartition des chiffres d'un camp à 15 minutes, par palier moyen du camp",
            description = """
                    Écarts d'or, d'XP et de kills à l'autre camp, objectifs pris, ganks décisifs faits et
                    subis. Une partie par ligne : c'est une partie d'équipe qui se situe dedans, pas une moyenne.""")
    @GetMapping("/references/team")
    public ReferenceGrid teamGrid(@RequestParam(required = false) String patch,
                                  @RequestParam(required = false) String tier) {
        return (patch == null
                ? references.latest(ReferenceService.TEAM, ReferenceService.TEAM)
                : references.forPatch(ReferenceService.TEAM, ReferenceService.TEAM, patch))
                .map(reference -> references.toGrid(reference, tier))
                .orElseThrow(() -> new RiotResourceNotFoundException("Aucune référence d'équipe calculée."));
    }

    @Operation(summary = "Répartition des moyennes des joueurs d'un champion, dans le groupe de paliers donné",
            description = """
                    `tier` est le palier du joueur à noter ; il est ramené à son groupe (Or → SILVER_GOLD).
                    Une métrique absente de la réponse : pas assez de joueurs, se rabattre sur le poste.""")
    @GetMapping("/references/champions/{championId}")
    public ChampionReferenceGrid championGrid(@PathVariable int championId, @RequestParam String tier) {
        return references.championGrid(championId, tier)
                .orElseThrow(() -> new RiotResourceNotFoundException("Aucune référence calculée pour ce champion."));
    }

    @Operation(summary = "Répartition des métriques à un poste, par palier et sur tout le ladder",
            description = """
                    Recalculée chaque jour sur les deux derniers patchs, parties classées solo et flex.
                    Avec `patch`, la référence de ce patch s'il y en a une, sinon la plus récente. Avec
                    `tier`, seulement la grille de ce palier (plus celle du ladder) : c'est ce qu'il
                    faut pour noter un joueur, et vingt fois moins lourd.""")
    @GetMapping("/references/{position}")
    public ReferenceGrid referenceGrid(@PathVariable TeamPosition position,
                                       @RequestParam(defaultValue = ReferenceService.MEAN) String scope,
                                       @RequestParam(required = false) String patch,
                                       @RequestParam(required = false) String tier) {
        return (patch == null ? references.latest(scope, position.name()) : references.forPatch(scope, position.name(), patch))
                .map(reference -> references.toGrid(reference, tier))
                .orElseThrow(() -> new RiotResourceNotFoundException("Aucune référence calculée pour ce poste."));
    }

    @Operation(summary = "Référentiels du radar pour des joueurs à un poste",
            description = """
                    Pour chaque joueur : les bornes des joueurs collectés de son palier au même poste
                    (recalculées toutes les heures), et celles de ses adversaires directs sur la
                    période. **Aucun appel à Riot** : le palier vient du dernier rang relevé.

                    Un référentiel de moins de dix joueurs est rendu absent plutôt que bruité.""")
    @PostMapping("/references")
    public List<PlayerReferences> references(@Valid @RequestBody ReferencesQuery query) {
        return metricScale.references(query.players());
    }
}
