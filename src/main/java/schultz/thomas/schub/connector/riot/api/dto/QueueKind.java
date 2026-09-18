package schultz.thomas.schub.connector.riot.api.dto;

/**
 * La file de matchmaking, nommée plutôt que numérotée.
 *
 * <p>Le {@code queueId} brut reste transporté à côté : il est la donnée de Riot, ce nom n'en
 * est qu'une lecture. Le connecteur ne filtre jamais sur la file — savoir qu'une victoire en
 * normale draft ne vaut pas une victoire en flex est un jugement de domaine, donc du cœur.</p>
 *
 * <p>Valeurs relevées sur l'API réelle le 18-09 : 420 (solo/duo), 440 (flex), 400 (draft
 * normale) et 490 (quickplay) ont toutes rendu des parties sur des comptes EUW.</p>
 */
public enum QueueKind {

    /** 400 — Faille de l'invocateur, sélection en draft, non classée. */
    NORMAL_DRAFT,

    /** 430 — Faille de l'invocateur, sélection aveugle, non classée. */
    NORMAL_BLIND,

    /** 490 — « Partie rapide », la file sans sélection de champion en jeu. */
    QUICKPLAY,

    /** 420 — Classée solo/duo. */
    RANKED_SOLO,

    /** 440 — Classée flexible. */
    RANKED_FLEX,

    /** 450 — ARAM. */
    ARAM,

    /** 700 — Clash. */
    CLASH,

    /** 0 — Partie personnalisée. L'équipe n'en joue pas, mais la valeur existe. */
    CUSTOM,

    /** Tout le reste : rotations, modes éphémères, files à venir. */
    OTHER;

    /** Une file inconnue n'est jamais une erreur : Riot en ajoute, le connecteur survit. */
    public static QueueKind fromQueueId(int queueId) {
        return switch (queueId) {
            case 0 -> CUSTOM;
            case 400 -> NORMAL_DRAFT;
            case 420 -> RANKED_SOLO;
            case 430 -> NORMAL_BLIND;
            case 440 -> RANKED_FLEX;
            case 450 -> ARAM;
            case 490 -> QUICKPLAY;
            case 700 -> CLASH;
            default -> OTHER;
        };
    }
}
