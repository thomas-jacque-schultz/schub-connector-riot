package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService;
import schultz.thomas.schub.connector.riot.business.services.RankHistory;
import schultz.thomas.schub.connector.riot.business.stats.ReferenceService;

// Les projections se recalculent depuis le brut stocké : aucun appel à Riot.
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectionUpgrade {

    private final ParticipationProjector projector;
    private final MatchEnrichmentService enrichment;
    private final RankHistory rankHistory;
    private final ReferenceService references;
    private final TeamSideProjector teamSides;

    @EventListener(ApplicationReadyEvent.class)
    public void upgrade() {
        rankHistory.backfillIfEmpty();
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
