package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * @param tracked      un curseur d'historique existe : c'est ce joueur-là qu'on collecte. Faux
 *                     avec des parties quand même signifie qu'on ne le connaît que pour l'avoir
 *                     croisé dans l'historique d'un autre — vue partielle par construction.
 * @param knownMatches ids relevés pour lui ; l'écart avec {@code analysedMatches} est ce dont le
 *                     détail reste à collecter.
 */
@Schema(description = "L'assise des statistiques d'un joueur.")
public record PlayerCoverage(
        String puuid,
        boolean tracked,
        long knownMatches,
        long analysedMatches,
        Instant firstPlayedAt,
        Instant lastPlayedAt,
        Instant firstSyncAt,
        Instant lastSyncAt
) {
}
