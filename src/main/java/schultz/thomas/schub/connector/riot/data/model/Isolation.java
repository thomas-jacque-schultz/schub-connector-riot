package schultz.thomas.schub.connector.riot.data.model;

// Un joueur sur les images comptées : seul quand aucun coéquipier en jeu n'est à moins de 2 000 unités.
public record Isolation(
        String puuid,
        int side,
        int earlyMinutes,
        int earlyAlone,
        int lateMinutes,
        int lateAlone
) {
}
