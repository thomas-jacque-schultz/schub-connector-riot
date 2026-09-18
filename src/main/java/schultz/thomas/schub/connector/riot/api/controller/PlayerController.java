package schultz.thomas.schub.connector.riot.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import schultz.thomas.schub.connector.riot.api.dto.ChampionMastery;
import schultz.thomas.schub.connector.riot.api.dto.HistorySyncReport;
import schultz.thomas.schub.connector.riot.api.dto.MatchHistory;
import schultz.thomas.schub.connector.riot.api.dto.PlayerIdentity;
import schultz.thomas.schub.connector.riot.api.dto.RankedStanding;
import schultz.thomas.schub.connector.riot.business.services.ChampionMasteryService;
import schultz.thomas.schub.connector.riot.business.services.MatchHistoryService;
import schultz.thomas.schub.connector.riot.business.services.PlayerIdentityService;
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

    @Operation(summary = "Constituer ou prolonger l'historique d'un joueur",
            description = """
                    Force un relevé auprès de Riot, sans attendre l'expiration de la fraîcheur.

                    Le premier remplissage se fait en plusieurs passes : les identifiants sont
                    tous relevés d'un coup — c'est bon marché — mais les détails sont récupérés
                    par paquets bornés, pour ne pas faire pendre cette requête le temps que le
                    quota s'écoule. `detailsPending` dit ce qui reste ; rappeler cette route
                    reprend où la précédente s'est arrêtée.""")
    @PostMapping("/{puuid}/matches/sync")
    public HistorySyncReport sync(@PathVariable String puuid) {
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
