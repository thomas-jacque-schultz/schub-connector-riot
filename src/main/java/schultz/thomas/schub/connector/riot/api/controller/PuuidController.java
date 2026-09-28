package schultz.thomas.schub.connector.riot.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import schultz.thomas.schub.connector.riot.api.dto.PuuidListRequest;
import schultz.thomas.schub.connector.riot.business.services.PuuidValidityService;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/puuids")
@Tag(name = "Puuid", description = "Riot chiffre le puuid pour chaque clé : ceux d'une autre clé sont refusés.")
public class PuuidController {

    private final PuuidValidityService validity;

    @Operation(summary = "Les puuid refusés par Riot depuis une date",
            description = "Le cœur s'en sert pour résoudre de nouveau le Riot ID des comptes liés et des places d'équipe.")
    @GetMapping("/stale")
    public List<String> stale(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        return validity.staleSince(since);
    }

    @Operation(summary = "Vérifier des puuid",
            description = "Rend ceux qu'on sait déjà refusés ; les autres, pas vérifiés depuis une semaine, partent en "
                    + "file et apparaîtront dans /puuids/stale s'ils sont refusés.")
    @PostMapping("/check")
    public List<String> check(@RequestBody PuuidListRequest request) {
        return validity.check(request.puuids());
    }
}
