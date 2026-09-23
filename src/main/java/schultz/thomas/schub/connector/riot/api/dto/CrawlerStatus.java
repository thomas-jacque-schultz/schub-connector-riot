package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "La collecte de fond : elle relève l'historique des comptes croisés, sans qu'on le demande.")
public record CrawlerStatus(
        @Schema(description = "Faux en prod : la collecte de fond y tourne toujours.") boolean switchable,
        boolean enabled,
        @Schema(description = "Activée et sous le seuil de volume.") boolean running,
        long knownAccounts,
        long trackedAccounts,
        long backgroundPending,
        long databaseBytes,
        long storageAlertBytes,
        @Schema(description = "Le volume de la base dépasse le seuil : la collecte de fond est suspendue.")
        boolean storageAlert,
        @Schema(description = "Absent tant qu'aucun tour n'a empilé de compte.") Instant lastRoundAt,
        int lastRoundAccounts
) {
}
