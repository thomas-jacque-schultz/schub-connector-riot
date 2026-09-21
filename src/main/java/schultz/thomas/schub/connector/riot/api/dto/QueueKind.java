package schultz.thomas.schub.connector.riot.api.dto;

/**
 * La file de matchmaking, nommée plutôt que numérotée.
 *
 * <p>Le {@code queueId} brut reste transporté à côté : il est la donnée de Riot, ce nom n'en
 * est qu'une lecture. Le connecteur ne filtre jamais sur la file — savoir qu'une victoire en
 * normale draft ne vaut pas une victoire en flex est un jugement de domaine, donc du cœur.</p>
 *
 * <p>La correspondance est reprise de la liste officielle publiée par Riot
 * (<a href="https://static.developer.riotgames.com/docs/lol/queues.json">queues.json</a>), relevée
 * le 22-09-2026. Les files qu'elle marque dépréciées n'y figurent pas : elles ne peuvent plus
 * apparaître dans un historique {@code match-v5}. Les modes éphémères depuis longtemps retirés
 * non plus — ils tombent en {@link #OTHER}, qui est un nom lisible et pas un numéro.</p>
 *
 * <p>Plusieurs identifiants donnent le même mode — l'arène en a deux, l'entraînement contre l'IA
 * en a quatre. C'est la raison d'être de cette énumération : « 3 parties d'Arène » se lit,
 * « 2 parties en file 1700 et 1 en file 1710 » ne se lit pas.</p>
 */
public enum QueueKind {

    /** 400 — Faille de l'invocateur, sélection en draft, non classée. */
    NORMAL_DRAFT,

    /** 430 — Faille de l'invocateur, sélection aveugle, non classée. */
    NORMAL_BLIND,

    /** 490 — « Partie rapide », la file sans sélection de champion en jeu. */
    QUICKPLAY,

    /** 480 — « Swiftplay », la file d'initiation apparue en 2025. */
    SWIFTPLAY,

    /** 420 — Classée solo/duo. */
    RANKED_SOLO,

    /** 440 — Classée flexible. */
    RANKED_FLEX,

    /** 450 — ARAM. */
    ARAM,

    /** 2400 — ARAM Mayhem, la variante à modificateurs. */
    ARAM_MAYHEM,

    /** 700 — Clash. */
    CLASH,

    /** 720 — Clash en ARAM. */
    ARAM_CLASH,

    /** 820, 870, 880, 890 — entraînement contre l'IA, tous niveaux de bots confondus. */
    COOP_VS_AI,

    /** 900, 1010 — ARURF, champion aléatoire. */
    ARURF,

    /** 76, 1900 — URF avec choix du champion. */
    URF,

    /** 1020 — Un pour tous. */
    ONE_FOR_ALL,

    /** 1300 — Nexus Blitz. */
    NEXUS_BLITZ,

    /** 1400 — Grimoire ultime. */
    ULTIMATE_SPELLBOOK,

    /** 1700, 1710 — Arène. */
    ARENA,

    /** 1810 à 1840 — Swarm, du solo au groupe de quatre. */
    SWARM,

    /** 2300 — Brawl. */
    BRAWL,

    /** 2000, 2010, 2020 — les trois tutoriels. */
    TUTORIAL,

    /** 0 — Partie personnalisée. L'équipe n'en joue pas, mais la valeur existe. */
    CUSTOM,

    /** Tout le reste : rotations passées, modes éphémères, files à venir. */
    OTHER;

    /** Une file inconnue n'est jamais une erreur : Riot en ajoute, le connecteur survit. */
    public static QueueKind fromQueueId(int queueId) {
        return switch (queueId) {
            case 0 -> CUSTOM;
            case 76, 1900 -> URF;
            case 400 -> NORMAL_DRAFT;
            case 420 -> RANKED_SOLO;
            case 430 -> NORMAL_BLIND;
            case 440 -> RANKED_FLEX;
            case 450 -> ARAM;
            case 480 -> SWIFTPLAY;
            case 490 -> QUICKPLAY;
            case 700 -> CLASH;
            case 720 -> ARAM_CLASH;
            case 820, 870, 880, 890 -> COOP_VS_AI;
            case 900, 1010 -> ARURF;
            case 1020 -> ONE_FOR_ALL;
            case 1300 -> NEXUS_BLITZ;
            case 1400 -> ULTIMATE_SPELLBOOK;
            case 1700, 1710 -> ARENA;
            case 1810, 1820, 1830, 1840 -> SWARM;
            case 2000, 2010, 2020 -> TUTORIAL;
            case 2300 -> BRAWL;
            case 2400 -> ARAM_MAYHEM;
            default -> OTHER;
        };
    }
}
