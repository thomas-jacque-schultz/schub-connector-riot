package schultz.thomas.schub.connector.riot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ce qu'un empilement a ajouté à la file.")
public record IngestEnqueueReportDto(String puuid, boolean queued, IngestStatusDto status) {
}
