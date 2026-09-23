package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService;
import schultz.thomas.schub.connector.riot.business.services.RankHistory;
import schultz.thomas.schub.connector.riot.business.stats.ReferenceService;

import java.util.List;

// Les projections se recalculent depuis le brut stocké : aucun appel à Riot.
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectionUpgrade {

    // Dérivées, remplacées par riot_reference : se recalculaient toutes les heures sur tout l'historique.
    private static final List<String> OBSOLETES = List.of("riot_player_position", "riot_metric_scale");

    private final ParticipationProjector projector;
    private final MatchEnrichmentService enrichment;
    private final RankHistory rankHistory;
    private final ReferenceService references;
    private final TeamSideProjector teamSides;
    private final MongoTemplate mongo;

    @EventListener(ApplicationReadyEvent.class)
    public void upgrade() {
        rankHistory.backfillIfEmpty();
        OBSOLETES.stream().filter(mongo::collectionExists).forEach(mongo::dropCollection);
        if (projector.outdated()) {
            log.info("Participations projetées par une version antérieure : reprojection sur place.");
            projector.upgradeOutdated();
        }
        int debuts = enrichment.recalculePerimes();
        if (debuts > 0) {
            log.info("{} chiffres à 15 minutes recalculés depuis leur timeline.", debuts);
        }
        teamSides.backfill();
        if (references.missing()) {
            references.refresh();
        }
    }
}
