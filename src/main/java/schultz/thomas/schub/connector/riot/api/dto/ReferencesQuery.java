package schultz.thomas.schub.connector.riot.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record ReferencesQuery(@NotEmpty @Size(max = 50) List<@Valid Player> players) {

    public record Player(@NotBlank String puuid, @NotNull TeamPosition position, Instant since) {
    }
}
