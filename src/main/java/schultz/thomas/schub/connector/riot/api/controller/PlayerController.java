package schultz.thomas.schub.connector.riot.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import schultz.thomas.schub.connector.riot.api.dto.ChampionMastery;
import schultz.thomas.schub.connector.riot.api.dto.HistorySyncReport;
import schultz.thomas.schub.connector.riot.api.dto.IngestEnqueueReport;
import schultz.thomas.schub.connector.riot.api.dto.MatchHistory;
import schultz.thomas.schub.connector.riot.api.dto.PlayerIdentity;
import schultz.thomas.schub.connector.riot.api.dto.PlayerSuggestion;
import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;
import schultz.thomas.schub.connector.riot.business.ingest.IngestService;
import schultz.thomas.schub.connector.riot.business.services.ChampionMasteryService;
import schultz.thomas.schub.connector.riot.business.services.MatchHistoryService;
import schultz.thomas.schub.connector.riot.business.services.PlayerIdentityService;
import schultz.thomas.schub.connector.riot.business.search.PlayerSearchService;
import schultz.thomas.schub.connector.riot.business.services.RankingService;

import java.time.Instant;
import java.util.List;

/**
 * Ce qu'on peut savoir d'un joueur : qui il est, ce qu'il a joué, où il en est.
 *
 * <p>Les routes sont nommées en vocabulaire de domaine et non en décalque de l'API Riot :
 * l'appelant demande les <em>parties</em> d'un <em>joueur</em>, il n'a pas à savoir que cela
 * s'appelle {@code match-v5} ni que c'est sur une route régionale.</p>
 *
 * <p>Protégées comme le reste du maillage par {@code X-Internal-Secret} ; seul
 * {@code GET /actuator/health} est ouvert.</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/players")
@Tag(name = "Joueurs", description = "Identité, historique, classements et maîtrises.")
public class PlayerController {

    private final PlayerIdentityService identityService;
    private final MatchHistoryService historyService;
    private final RankingService rankingService;
    private final ChampionMasteryService masteryService;
    private final IngestService ingestService;
    private final PlayerSearchService playerSearchService;

    @Operation(summary = "Résoudre un Riot ID en joueur",
            description = """
                    `Pseudo#TAG` → `puuid`. C'est l'appel de la liaison de compte.

                    Le `puuid` est la **seule** clé stable : un joueur peut changer de Riot ID,
                    donc un pseudo ne doit jamais servir d'identifiant. Sans cache, volontairement —
                    garder une correspondance périmée reviendrait à répondre faux.

                    404 si ce Riot ID n'existe pas.""")
    @GetMapping
    public PlayerIdentity resolve(
            @Parameter(description = "Partie gauche du Riot ID.", example = "J1HUIV")
            @RequestParam @NotBlank String gameName,
            @Parameter(description = "Partie droite du Riot ID, sans le #.", example = "000")
            @RequestParam @NotBlank String tagLine) {
        return identityService.resolve(gameName, tagLine);
    }

    @Operation(summary = "Chercher un compte parmi nos participations",
            description = """
                    **L'API Riot ne sait pas chercher par pseudo partiel** : `account-v1` ne
                    résout qu'un `gameName#tagLine` exact, `summoner-v4/by-name` n'existe plus.
                    Cette route ne l'appelle donc pas : elle cherche dans les Riot ID que le brut
                    de `match-v5` porte pour chacun des dix participants de chaque partie
                    collectée — un seul compte ingéré en fait connaître environ 2 150.

                    Casse et accents ignorés, sous-chaîne acceptée, et une faute de frappe tolérée
                    tous les quatre caractères à condition que les trois premiers soient bons.
                    `Pseudo#TAG` collé entier est compris : le tag filtre alors les homonymes.

                    Les chiffres rendus portent sur **nos** données — parties où on a croisé ce
                    joueur, postes qu'on l'y a vu tenir, dernière d'entre elles. Ils servent à
                    reconnaître son propre compte, pas à décrire une carrière.

                    Liste vide si rien ne ressemble. Ce n'est pas une erreur : la résolution
                    exacte d'un Riot ID reste le chemin toujours disponible.""")
    @GetMapping("/search")
    public List<PlayerSuggestion> search(
            @Parameter(description = "Pseudo, même partiel. `Pseudo#TAG` accepté.", example = "J1HUIV")
            @RequestParam @NotBlank String q,
            @Parameter(description = "Nombre de propositions rendues.", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        return playerSearchService.search(q, limit);
    }

    @Operation(summary = "Le Riot ID courant d'un joueur",
            description = "L'inverse de la résolution : c'est par là qu'on rattrape un "
                    + "changement de Riot ID sur un compte déjà lié.")
    @GetMapping("/{puuid}")
    public PlayerIdentity identify(@PathVariable String puuid) {
        return identityService.identify(puuid);
    }

    @Operation(summary = "Les parties d'un joueur depuis une date",
            description = """
                    Rend les identifiants de parties connus, servis du cache du connecteur.

                    L'historique est **append-only** : Riot n'est rappelé que si le dernier relevé
                    est plus vieux que la fraîcheur configurée, et alors uniquement pour ce qui
                    suit ce relevé — moins une heure de recouvrement, parce qu'une partie peut
                    apparaître avec du retard.

                    `refreshed` dit si cet appel a interrogé Riot ; `syncedAt` date le dernier
                    relevé. Si Riot est injoignable, la réponse reste servie depuis le cache avec
                    `refreshed = false` plutôt que d'échouer.

                    Les parties dont le détail n'a pas encore été récupéré sont incluses : leur
                    id est connu, leur date ne l'est pas encore.""")
    @GetMapping("/{puuid}/matches")
    public MatchHistory matches(
            @PathVariable String puuid,
            @Parameter(description = "Borne basse, ISO-8601. Absente = tout l'historique connu.",
                    example = "2026-09-01T00:00:00Z")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        return historyService.history(puuid, since);
    }

    @Operation(summary = "Empiler la collecte de l'historique d'un joueur",
            description = """
                    **Empile, puis répond immédiatement.** Un premier remplissage coûte un appel
                    par partie et Riot en garde environ mille par joueur : compter dessus dans le
                    temps d'une requête HTTP n'a pas de sens.

                    L'empilement est idempotent — redemander pendant que la collecte tourne ne
                    duplique rien — et `status` donne le temps d'écoulement estimé.""")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/{puuid}/matches/sync")
    public IngestEnqueueReport sync(@PathVariable String puuid) {
        return ingestService.enqueuePlayer(puuid);
    }

    @Operation(summary = "Relever l'historique tout de suite, sans passer par la file",
            description = """
                    Voie synchrone, conservée pour le diagnostic : quand la file n'avance pas,
                    elle rend l'erreur de Riot dans la réponse au lieu de la laisser dans les
                    journaux.

                    Bornée par `max-details-per-call` ; `detailsPending` dit ce qui reste. Ce
                    n'est pas la voie normale — pour constituer un historique, empiler.""")
    @PostMapping("/{puuid}/matches/sync-now")
    public HistorySyncReport syncNow(@PathVariable String puuid) {
        return historyService.sync(puuid);
    }

    @Operation(summary = "Rang et LP d'un joueur",
            description = """
                    Une entrée par file classée jouée. Relevé mis en cache **une heure** :
                    afficher un LP d'hier serait un bug visible.

                    Chaque entrée porte `observedAt`, la date de son relevé. Si Riot est
                    injoignable, le dernier relevé connu est servi tel quel — c'est `observedAt`
                    qui dit l'âge de ce qu'on affiche.""")
    @GetMapping("/{puuid}/rankings")
    public List<RankedStanding> rankings(@PathVariable String puuid) {
        return rankingService.rankings(puuid);
    }

    @Operation(summary = "Maîtrises de champions d'un joueur",
            description = """
                    Du plus maîtrisé au moins maîtrisé. Cache de **six heures** : un pool de
                    champions n'a pas besoin d'être à la seconde.

                    Le connecteur rend des maîtrises, pas un « pool jouable par poste » : décider
                    qu'un joueur peut tenir un poste est un jugement de domaine, et il vit dans
                    le cœur.""")
    @GetMapping("/{puuid}/champion-mastery")
    public List<ChampionMastery> masteries(
            @PathVariable String puuid,
            @Parameter(description = "Nombre de champions rendus. Absent = tous.", example = "10")
            @RequestParam(required = false) Integer limit) {
        return masteryService.masteries(puuid, limit);
    }
}
