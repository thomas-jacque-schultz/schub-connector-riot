package schultz.thomas.schub.connector.riot.data.model;

// Une paire de coéquipiers, sur les images de la timeline (une par minute) où les deux sont en jeu.
public record Proximity(
        String puuid,
        String matePuuid,
        int side,
        int earlyMinutes,
        int earlyTogether,
        int lateMinutes,
        int lateTogether,
        Integer meanDistance,
        int earlyDepartures
) {
}
