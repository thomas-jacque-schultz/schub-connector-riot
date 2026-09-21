package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * @param unusable parties stockées avant le passage au JSON brut : sans {@code raw}, rien n'en
 *                 est reconstructible et elles doivent être recollectées.
 */
@Schema(description = "Reconstruction de la couche d'analyse depuis le stocké, sans aucun appel à Riot.")
public record RebuildReport(
        int matchesRead,
        int participationsWritten,
        int unusableMatches,
        Instant startedAt,
        Instant finishedAt
) {
}
