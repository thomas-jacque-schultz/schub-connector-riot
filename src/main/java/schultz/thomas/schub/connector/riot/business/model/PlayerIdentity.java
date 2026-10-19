package schultz.thomas.schub.connector.riot.business.model;

public record PlayerIdentity(
        String puuid,
        String gameName,
        String tagLine
) {

    public String riotId() {
        return gameName + "#" + tagLine;
    }
}
