package schultz.thomas.schub.connector.riot.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import schultz.thomas.schub.connector.riot.api.dto.CrawlerStatus;
import schultz.thomas.schub.connector.riot.api.dto.CrawlerToggleRequest;
import schultz.thomas.schub.connector.riot.api.dto.IngestStatus;
import schultz.thomas.schub.connector.riot.api.dto.KnownAccountRebuildReport;
import schultz.thomas.schub.connector.riot.api.dto.PlayerIngestStatus;
import schultz.thomas.schub.connector.riot.api.dto.RebuildReport;
import schultz.thomas.schub.connector.riot.api.dto.SamplingStatus;
import schultz.thomas.schub.connector.riot.business.ingest.BackgroundCrawler;
import schultz.thomas.schub.connector.riot.business.ingest.IngestService;
import schultz.thomas.schub.connector.riot.business.ingest.LadderSampler;
import schultz.thomas.schub.connector.riot.business.ingest.ParticipationProjector;
import schultz.thomas.schub.connector.riot.business.search.KnownAccountIndex;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ingest")
@Tag(name = "Ingestion", description = "File de collecte, avancement et reconstruction.")
public class IngestController {

    private final IngestService ingestService;
    private final ParticipationProjector projector;
    private final KnownAccountIndex knownAccounts;
    private final BackgroundCrawler crawler;
    private final LadderSampler sampler;

    @Operation(summary = "Où en est la collecte",
            description = """
                    En attente, en cours, en échec — et surtout le **temps d'écoulement estimé**
                    au débit autorisé courant.

                    C'est le seul chiffre qui réponde à « quand mes données seront-elles
                    prêtes ? » : « 4 300 en attente » ne dit rien, « prêt dans 1 h 25 » si.
                    Une pénalité 429 en cours est ajoutée à l'estimation.""")
    @GetMapping
    public IngestStatus status() {
        return ingestService.status();
    }

    @Operation(summary = "La collecte de fond",
            description = "Toujours active en prod ; en dev, `switchable` permet de la basculer. "
                    + "Elle se suspend d'elle-même au-delà du seuil de volume de la base.")
    @GetMapping("/crawler")
    public CrawlerStatus crawler() {
        return crawler.status();
    }

    @Operation(summary = "L'échantillon par palier",
            description = "Graines tirées dans les classements, par palier, sur la fenêtre glissante. "
                    + "Suit la collecte de fond : suspendu quand elle l'est.")
    @GetMapping("/sampling")
    public SamplingStatus sampling() {
        return sampler.status();
    }

    @Operation(summary = "Basculer la collecte de fond", description = "409 là où elle n'est pas basculable.")
    @PutMapping("/crawler")
    public CrawlerStatus toggleCrawler(@RequestBody CrawlerToggleRequest request) {
        return crawler.toggle(request.enabled());
    }

    @Operation(summary = "Où en est la collecte d'un joueur",
            description = """
                    Le même chiffre que ci-dessus, mais pour un seul `puuid` : c'est ce qu'un
                    écran de profil peut afficher, là où l'état global de la file ne lui dit rien.

                    `estimatedReadyAt` est nul quand rien n'est en file pour ce joueur. Un `puuid`
                    inconnu répond comme un joueur sans travail en attente : tout est à zéro,
                    plutôt qu'un 404 que l'appelant devrait traduire.""")
    @GetMapping("/players/{puuid}")
    public PlayerIngestStatus playerStatus(@PathVariable String puuid) {
        return ingestService.statusOf(puuid);
    }

    @Operation(summary = "Les tâches abandonnées",
            description = "Une tâche épuisée en tentatives n'est jamais reprise d'elle-même. "
                    + "Elle est donc ici, avec sa dernière erreur, plutôt que perdue.")
    @GetMapping("/failures")
    public List<IngestTask> failures() {
        return ingestService.failures();
    }

    @Operation(summary = "Réarmer les tâches abandonnées",
            description = "Geste explicite : un empilement ordinaire ne réarme pas un échec, "
                    + "sinon une partie définitivement absente serait redemandée sans fin.")
    @PostMapping("/retry-failed")
    public long retryFailed() {
        return ingestService.retryFailed();
    }

    @Operation(summary = "Reconstruire la couche d'analyse",
            description = """
                    Recalcule `riot_participation` depuis `riot_match`, **sans un seul appel à
                    Riot**. C'est la propriété que le stockage du JSON brut sert à préserver :
                    changer le modèle d'analyse ne coûte que cet appel.

                    `unusableMatches` compte les parties stockées avant le passage au brut :
                    elles ne produisent aucune participation et doivent être recollectées.""")
    @PostMapping("/participations/rebuild")
    public RebuildReport rebuild() {
        return projector.rebuildAll();
    }

    @Operation(summary = "Reprojeter les participations dans l'index des comptes connus",
            description = """
                    Rejoue `riot_participation` dans `riot_known_account`, **sans aucun appel à
                    Riot** — les participations sont elles-mêmes reconstructibles depuis
                    `riot_match`.

                    **Ne purge pas.** Un compte confirmé par Riot et jamais croisé en partie
                    n'existe nulle part ailleurs : l'effacer pour le reconstruire le perdrait.
                    Une entrée n'est réécrite que si l'observation rejouée est plus récente que
                    celle en place, donc rejouer deux fois ne change rien.""")
    @PostMapping("/known-accounts/rebuild")
    public KnownAccountRebuildReport rebuildKnownAccounts() {
        return knownAccounts.rebuildFromParticipations();
    }
}
