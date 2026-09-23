package schultz.thomas.schub.connector.riot.business.search;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.PlayerSuggestion;
import schultz.thomas.schub.connector.riot.api.dto.TeamPosition;
import schultz.thomas.schub.connector.riot.data.model.KnownAccount;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Service
public class PlayerSearchService {

    private static final int LONGUEUR_PREFIXE = 3;

    private static final int CANDIDATS_MAX = 50;

    private static final int POSTES_RENDUS = 3;

    private final MongoTemplate mongo;

    public List<PlayerSuggestion> search(String saisie, int limit) {
        Recherche recherche = Recherche.de(saisie);
        if (recherche == null) {
            return List.of();
        }

        Collection<KnownAccount> retenus = unParRiotId(candidats(recherche).stream()
                .filter(recherche::retient)
                .toList());
        Map<String, Compteurs> compteurs = compteurs(retenus.stream().map(KnownAccount::puuid).toList());

        return retenus.stream()
                .map(compte -> toSuggestion(compte, compteurs.get(compte.puuid())))
                .sorted(Comparator.comparingInt((PlayerSuggestion trouve) -> recherche.rang(trouve.gameName()))
                        .thenComparing(Comparator.comparingLong(PlayerSuggestion::matchCount).reversed())
                        .thenComparing(PlayerSuggestion::observedAt, Comparator.reverseOrder()))
                .limit(Math.max(1, limit))
                .toList();
    }

    private List<KnownAccount> candidats(Recherche recherche) {
        Criteria sousChaine = Criteria.where("searchName").regex(Pattern.quote(recherche.pseudo()));
        Criteria critere = recherche.pseudo().length() < LONGUEUR_PREFIXE
                ? sousChaine
                : new Criteria().orOperator(sousChaine, Criteria.where("searchName")
                        .regex("^" + Pattern.quote(recherche.pseudo().substring(0, LONGUEUR_PREFIXE))));

        return mongo.find(Query.query(critere)
                .with(Sort.by(Sort.Direction.DESC, "observedAt"))
                .limit(CANDIDATS_MAX), KnownAccount.class);
    }

    private static Collection<KnownAccount> unParRiotId(List<KnownAccount> comptes) {
        Map<String, KnownAccount> parRiotId = new LinkedHashMap<>();
        for (KnownAccount compte : comptes) {
            String cle = compte.riotId() == null ? compte.puuid()
                    : compte.riotId().toLowerCase(Locale.ROOT);
            parRiotId.merge(cle, compte,
                    (enPlace, autre) -> autre.observedAt().isAfter(enPlace.observedAt()) ? autre : enPlace);
        }
        return parRiotId.values();
    }

    private Map<String, Compteurs> compteurs(Collection<String> puuids) {
        if (puuids.isEmpty()) {
            return Map.of();
        }
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("puuid").in(puuids)),
                Aggregation.group("puuid")
                        .max("startedAt").as("lastPlayedAt")
                        .count().as("matchCount")
                        .push("position").as("positions"));

        Map<String, Compteurs> parPuuid = new LinkedHashMap<>();
        for (Document row : mongo.aggregate(aggregation, MatchParticipation.class, Document.class)) {
            parPuuid.put(row.getString("_id"), new Compteurs(
                    ((Number) row.getOrDefault("matchCount", 0)).longValue(),
                    postes(row.getList("positions", String.class, List.of())),
                    instant(row.get("lastPlayedAt"))));
        }
        return parPuuid;
    }

    private PlayerSuggestion toSuggestion(KnownAccount compte, Compteurs compteurs) {
        Compteurs mesures = compteurs == null ? Compteurs.AUCUNE : compteurs;
        return new PlayerSuggestion(
                compte.puuid(),
                compte.gameName(),
                compte.tagLine(),
                compte.riotId(),
                mesures.matchCount(),
                mesures.positions(),
                mesures.lastPlayedAt(),
                compte.observedAt(),
                compte.source());
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

    private record Compteurs(long matchCount, List<PlayerSuggestion.PositionPlayed> positions,
                             Instant lastPlayedAt) {

        static final Compteurs AUCUNE = new Compteurs(0, List.of(), null);
    }

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

        boolean retient(KnownAccount compte) {
            if (tag != null && !tag.isBlank()) {
                String tagCandidat = SearchName.fold(compte.tagLine());
                if (tagCandidat == null || !tagCandidat.startsWith(tag)) {
                    return false;
                }
            }
            return rang(compte.gameName()) < RANG_ECARTE;
        }

        int rang(String gameName) {
            String candidat = SearchName.fold(gameName);
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

        private int toleranceFautes() {
            return Math.max(1, pseudo.length() / 4);
        }

        private static final int RANG_ECARTE = 9;
    }
}
