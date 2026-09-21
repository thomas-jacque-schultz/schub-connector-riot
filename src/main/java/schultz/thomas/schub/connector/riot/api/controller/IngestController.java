package schultz.thomas.schub.connector.riot.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import schultz.thomas.schub.connector.riot.api.dto.IngestStatus;
import schultz.thomas.schub.connector.riot.api.dto.RebuildReport;
import schultz.thomas.schub.connector.riot.business.ingest.IngestService;
import schultz.thomas.schub.connector.riot.business.ingest.ParticipationProjector;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;

import java.util.List;

/** L'état de la collecte, et les deux gestes d'exploitation qui vont avec. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ingest")
@Tag(name = "Ingestion", description = "File de collecte, avancement et reconstruction.")
public class IngestController {

    private final IngestService ingestService;
    private final ParticipationProjector projector;

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
}
