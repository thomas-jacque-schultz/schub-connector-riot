package schultz.thomas.schub.connector.riot.data.model;

public enum IngestTaskState {

    PENDING,

    /** Réclamée par un ouvrier. Un bail dépassé la rend de nouveau réclamable. */
    RUNNING,

    /** Épuisée en tentatives. N'est jamais reprise d'elle-même. */
    FAILED
}
