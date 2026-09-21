package schultz.thomas.schub.connector.riot.business.search;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.PlayerSuggestion;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Chercher un compte <strong>dans nos participations</strong>, pas chez Riot.
 *
 * <h2>Pourquoi ce service existe</h2>
 *
 * <p>L'API Riot ne sait pas chercher par pseudo partiel : {@code account-v1} ne résout qu'un
 * {@code gameName#tagLine} exact et {@code summoner-v4/by-name} n'existe plus. Mais le brut de
 * {@code match-v5} porte le Riot ID de <em>chacun</em> des dix participants : un seul compte
 * collecté fait connaître environ 2 150 joueurs, et c'est là-dessus qu'on cherche.</p>
 *
 * <h2>Ce que « approximatif » veut dire ici, exactement</h2>
 *
 * <p>Deux façons d'être candidat, réunies en une seule requête :</p>
 * <ol>
 *   <li><strong>sous-chaîne</strong> du pseudo replié — casse et accents ignorés ;</li>
 *   <li><strong>même début</strong> (trois caractères) et distance d'édition faible, ce qui
 *       rattrape la faute de frappe qui ne partage aucune sous-chaîne avec la cible.</li>
 * </ol>
 *
 * <p>Le second critère est ancré ({@code ^abc}), donc il se sert de l'index sur
 * {@code searchName} ; c'est ce qui permet de ne pas balayer la collection à chaque frappe. Une
 * saisie de moins de trois caractères n'ouvre que le premier critère.</p>
 */
@RequiredArgsConstructor
@Service
public class PlayerSearchService {

    /** En deçà, un préfixe ne discrimine plus rien et la tolérance aux fautes n'a pas de sens. */
    private static final int LONGUEUR_PREFIXE = 3;

    /** Les candidats les plus vus, avant classement par ressemblance. Borne le travail en mémoire. */
    private static final int CANDIDATS_MAX = 50;

    private static final int POSTES_RENDUS = 3;

    private final MongoTemplate mongo;

    public List<PlayerSuggestion> search(String saisie, int limit) {
        Recherche recherche = Recherche.de(saisie);
        if (recherche == null) {
            return List.of();
        }

        return candidats(recherche).stream()
                .filter(recherche::retient)
                .sorted(Comparator.comparingInt(recherche::rang)
                        .thenComparing(Comparator.comparingLong(PlayerSuggestion::matchCount).reversed()))
                .limit(Math.max(1, limit))
                .toList();
    }

    private List<PlayerSuggestion> candidats(Recherche recherche) {
        Criteria sousChaine = Criteria.where("searchName").regex(Pattern.quote(recherche.pseudo()));
        Criteria critere = recherche.pseudo().length() < LONGUEUR_PREFIXE
                ? sousChaine
                : new Criteria().orOperator(sousChaine, Criteria.where("searchName")
                        .regex("^" + Pattern.quote(recherche.pseudo().substring(0, LONGUEUR_PREFIXE))));

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(critere),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "startedAt"),
                Aggregation.group("puuid")
                        .first("gameName").as("gameName")
                        .first("tagLine").as("tagLine")
                        .first("searchName").as("searchName")
                        .max("startedAt").as("lastPlayedAt")
                        .count().as("matchCount")
                        .push("position").as("positions"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "matchCount"),
                Aggregation.limit(CANDIDATS_MAX));

        AggregationResults<Document> results =
                mongo.aggregate(aggregation, MatchParticipation.class, Document.class);
        return results.getMappedResults().stream().map(this::toSuggestion).toList();
    }

    private PlayerSuggestion toSuggestion(Document row) {
        String gameName = row.getString("gameName");
        String tagLine = row.getString("tagLine");
        return new PlayerSuggestion(
                row.getString("_id"),
                gameName,
                tagLine,
                riotId(gameName, tagLine),
                ((Number) row.getOrDefault("matchCount", 0)).longValue(),
                postes(row.getList("positions", String.class, List.of())),
                instant(row.get("lastPlayedAt")));
    }

    private List<PlayerSuggestion.PositionPlayed> postes(List<String> positions) {
        Map<TeamPosition, Long> parPoste = new EnumMap<>(TeamPosition.class);
        for (String position : positions) {
            TeamPosition poste = position == null ? TeamPosition.UNKNOWN : TeamPosition.valueOf(position);
            if (poste != TeamPosition.UNKNOWN) {
                parPoste.merge(poste, 1L, Long::sum);
            }
        }
        List<PlayerSuggestion.PositionPlayed> classes = new ArrayList<>();
        parPoste.forEach((poste, parties) -> classes.add(new PlayerSuggestion.PositionPlayed(poste, parties)));
        classes.sort(Comparator.comparingLong(PlayerSuggestion.PositionPlayed::matches).reversed());
        return classes.size() > POSTES_RENDUS ? List.copyOf(classes.subList(0, POSTES_RENDUS)) : List.copyOf(classes);
    }

    private static Instant instant(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        return value instanceof Instant instant ? instant : null;
    }

    private static String riotId(String gameName, String tagLine) {
        if (gameName == null || gameName.isBlank()) {
            return null;
        }
        return tagLine == null || tagLine.isBlank() ? gameName : gameName + "#" + tagLine;
    }

    /**
     * La saisie découpée une fois : pseudo replié, et le tag s'il a été donné.
     *
     * <p>Coller un Riot ID complet est le geste le plus naturel quand on l'a sous les yeux ;
     * sans ce découpage, {@code Pseudo#TAG} replié donnerait {@code pseudotag} et ne
     * ressemblerait plus à rien.</p>
     */
    private record Recherche(String pseudo, String tag) {

        static Recherche de(String saisie) {
            if (saisie == null || saisie.isBlank()) {
                return null;
            }
            int separateur = saisie.indexOf('#');
            String pseudo = SearchName.fold(separateur < 0 ? saisie : saisie.substring(0, separateur));
            String tag = separateur < 0 ? null : SearchName.fold(saisie.substring(separateur + 1));
            return pseudo == null ? null : new Recherche(pseudo, tag);
        }

        boolean retient(PlayerSuggestion suggestion) {
            if (tag != null && !tag.isBlank()) {
                String tagCandidat = SearchName.fold(suggestion.tagLine());
                if (tagCandidat == null || !tagCandidat.startsWith(tag)) {
                    return false;
                }
            }
            return rang(suggestion) < RANG_ECARTE;
        }

        int rang(PlayerSuggestion suggestion) {
            String candidat = SearchName.fold(suggestion.gameName());
            if (candidat == null) {
                return RANG_ECARTE;
            }
            if (candidat.equals(pseudo)) {
                return 0;
            }
            if (candidat.startsWith(pseudo)) {
                return 1;
            }
            if (candidat.contains(pseudo)) {
                return 2;
            }
            return EditDistance.between(pseudo, candidat) <= toleranceFautes() ? 3 : RANG_ECARTE;
        }

        /** Une faute tous les quatre caractères, au moins une. Au-delà, ce n'est plus le même pseudo. */
        private int toleranceFautes() {
            return Math.max(1, pseudo.length() / 4);
        }

        private static final int RANG_ECARTE = 9;
    }
}
