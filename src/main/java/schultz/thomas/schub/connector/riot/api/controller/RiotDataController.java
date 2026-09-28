package schultz.thomas.schub.connector.riot.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import schultz.thomas.schub.connector.riot.api.dto.RiotDataInventory;
import schultz.thomas.schub.connector.riot.api.dto.RiotDataPurgeReport;
import schultz.thomas.schub.connector.riot.business.services.RiotDataPurge;

@RestController
@RequiredArgsConstructor
@RequestMapping("/riot-data")
@Tag(name = "Données Riot", description = "Tout ce que la collecte a rapporté, à jeter après un changement de clé.")
public class RiotDataController {

    private final RiotDataPurge purge;

    @Operation(summary = "Ce qu'un effacement emporterait", description = "Nombres estimés, sans parcourir les collections.")
    @GetMapping
    public RiotDataInventory inventory() {
        return purge.inventory();
    }

    @Operation(summary = "Effacer les données Riot",
            description = """
                    Parties, participations, comptes connus, rangs, maîtrises, référentiels et file de collecte.
                    Restent les réglages de la collecte et les catalogues Data Dragon.

                    409 tant que l'ingest n'est pas en pause ou qu'une tâche est encore en route.
                    La double confirmation est l'affaire du cœur : ce service n'est joignable que par lui.""")
    @DeleteMapping
    public RiotDataPurgeReport purge() {
        return purge.purge();
    }
}
