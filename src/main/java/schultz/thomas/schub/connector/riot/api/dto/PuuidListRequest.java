package schultz.thomas.schub.connector.riot.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PuuidListRequest(@NotEmpty List<String> puuids) {
}
