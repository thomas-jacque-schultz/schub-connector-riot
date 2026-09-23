package schultz.thomas.schub.connector.riot.business.stats;

import org.bson.Document;

import java.util.List;

// Une métrique = numérateur / dénominateur par ligne : par partie on divise, sur une moyenne on divise les sommes.
public record ReferenceMetric(String key, Object numerator, Object denominator, Polarity polarity, boolean perGame,
                              Object meanDenominator) {

    public ReferenceMetric(String key, Object numerator, Object denominator, Polarity polarity, boolean perGame) {
        this(key, numerator, denominator, polarity, perGame, denominator);
    }

    public enum Polarity { HIGHER, LOWER, NEUTRAL }

    private static final Object MINUTES = new Document("$divide", List.of("$durationSeconds", 60));
    private static final Object PARTIE = 1;

    public static final List<ReferenceMetric> CATALOGUE = List.of(
            parMinute("csPerMinute", "$minionsKilled"),
            parMinute("goldPerMinute", "$goldEarned"),
            parMinute("visionPerMinute", "$visionScore"),
            parMinute("wardsKilledPerMinute", "$performance.wardsKilled"),
            new ReferenceMetric("controlWardsPlaced", "$performance.controlWardsPlaced", PARTIE, Polarity.HIGHER, true),
            parMinute("damagePerMinute", "$damageToChampions"),
            new ReferenceMetric("damageTakenPerMinute", "$damageTaken", MINUTES, Polarity.NEUTRAL, true),
            new ReferenceMetric("damageShare", "$damageToChampions", "$performance.teamDamageToChampions",
                    Polarity.HIGHER, true),
            new ReferenceMetric("killParticipation", new Document("$add", List.of("$kills", "$assists")), "$teamKills",
                    Polarity.HIGHER, true),
            new ReferenceMetric("deathShare", "$deaths", "$teamDeaths", Polarity.LOWER, true),
            new ReferenceMetric("deathsPer10", "$deaths", new Document("$divide", List.of("$durationSeconds", 600)),
                    Polarity.LOWER, true),
            // Par partie, une partie sans mort compte pour une ; en moyenne, (K + A) / D sur les sommes, comme le cœur.
            new ReferenceMetric("kda", new Document("$add", List.of("$kills", "$assists")),
                    new Document("$max", List.of("$deaths", 1)), Polarity.HIGHER, true, "$deaths"),
            new ReferenceMetric("timeDeadShare", "$performance.timeDeadSeconds", "$durationSeconds", Polarity.LOWER, true),
            parMinute("turretDamagePerMinute", "$performance.turretDamage"),
            new ReferenceMetric("turretTakedowns", "$performance.turretTakedowns", PARTIE, Polarity.HIGHER, true),
            parMinute("epicMonsterDamagePerMinute", "$performance.epicMonsterDamage"),
            parPartie("platesDiff", "$laning.platesDiff"),
            parPartie("goldDiffAt15", "$laning.goldDiffAt15"),
            parPartie("csDiffAt15", "$laning.csDiffAt15"),
            parPartie("xpDiffAt15", "$laning.xpDiffAt15"),
            parPartie("killsDiffAt15", "$laning.killsDiffAt15"),
            new ReferenceMetric("winRate", new Document("$cond", List.of("$win", 1, 0)), PARTIE, Polarity.HIGHER, false));

    // Un camp d'une partie à 15 minutes : écarts à l'autre camp, objectifs pris, ganks décisifs faits et subis.
    public static final List<ReferenceMetric> EQUIPE = List.of(
            parPartie("goldDiffAt15", "$goldDiffAt15"),
            parPartie("xpDiffAt15", "$xpDiffAt15"),
            parPartie("killsDiffAt15", "$killsDiffAt15"),
            parPartie("dragons", "$dragons"),
            parPartie("grubs", "$grubs"),
            parPartie("heralds", "$heralds"),
            parPartie("ganksDecisive", "$ganksDecisive"),
            new ReferenceMetric("ganksConceded", "$ganksConceded", PARTIE, Polarity.LOWER, true));

    private static ReferenceMetric parMinute(String key, String champ) {
        return new ReferenceMetric(key, champ, MINUTES, Polarity.HIGHER, true);
    }

    private static ReferenceMetric parPartie(String key, String champ) {
        return new ReferenceMetric(key, champ, PARTIE, Polarity.HIGHER, true);
    }
}
