package schultz.thomas.schub.connector.riot.api.dto;

public enum QueueKind {

    NORMAL_DRAFT,

    NORMAL_BLIND,

    QUICKPLAY,

    SWIFTPLAY,

    RANKED_SOLO,

    RANKED_FLEX,

    ARAM,

    ARAM_MAYHEM,

    CLASH,

    ARAM_CLASH,

    COOP_VS_AI,

    ARURF,

    URF,

    ONE_FOR_ALL,

    NEXUS_BLITZ,

    ULTIMATE_SPELLBOOK,

    ARENA,

    SWARM,

    BRAWL,

    TUTORIAL,

    CUSTOM,

    OTHER;

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
