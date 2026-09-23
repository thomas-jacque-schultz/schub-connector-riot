package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.EarlyGame;
import schultz.thomas.schub.connector.riot.api.dto.MatchInsights;
import schultz.thomas.schub.connector.riot.business.stats.ReferenceService;
import schultz.thomas.schub.connector.riot.data.model.MatchEarlyStats;
import schultz.thomas.schub.connector.riot.data.model.MatchParticipation;
import schultz.thomas.schub.connector.riot.data.model.TeamSide;
import schultz.thomas.schub.connector.riot.data.repository.MatchEarlyStatsRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchParticipationRepository;
import schultz.thomas.schub.connector.riot.data.repository.TeamSideRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.ToIntFunction;

// Lit les participations déjà projetées : leur rang est figé à la date de la partie.
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamSideProjector {

    private final MatchParticipationRepository participations;
    private final MatchEarlyStatsRepository earlyStats;
    private final TeamSideRepository sides;
    private final MongoTemplate mongo;

    public void project(String matchId) {
        earlyStats.findById(matchId).ifPresent(debut -> sides.saveAll(camps(debut,
                participations.findByMatchIdIn(List.of(matchId)))));
    }

    public int backfill() {
        Set<String> faits = new HashSet<>(ids(TeamSide.COLLECTION, "matchId"));
        int projetes = 0;
        for (String matchId : ids("riot_match_early", "_id")) {
            if (!faits.contains(matchId)) {
                project(matchId);
                projetes++;
            }
        }
        if (projetes > 0) {
            log.info("{} parties à timeline projetées par camp.", projetes);
        }
        return projetes;
    }

    private List<String> ids(String collection, String champ) {
        Query query = new Query();
        query.fields().include(champ);
        return mongo.find(query, Document.class, collection).stream().map(ligne -> String.valueOf(ligne.get(champ))).toList();
    }

    static List<TeamSide> camps(MatchEarlyStats debut, List<MatchParticipation> lignes) {
        if (lignes.isEmpty()) {
            return List.of();
        }
        MatchParticipation premiere = lignes.getFirst();
        Map<String, MatchInsights.At15> a15 = debut.byPuuid() == null ? Map.of() : debut.byPuuid();
        List<TeamSide> camps = new ArrayList<>();
        for (int side : List.of(100, 200)) {
            List<MatchParticipation> nous = lignes.stream().filter(ligne -> ligne.side() == side).toList();
            List<MatchParticipation> eux = lignes.stream().filter(ligne -> ligne.side() != side).toList();
            if (nous.isEmpty()) {
                continue;
            }
            EarlyGame early = debut.game();
            EarlyGame.Objectives objectifs = early == null ? null : early.objectives().stream()
                    .filter(o -> o.side() == side).findFirst().orElse(null);
            camps.add(new TeamSide(TeamSide.idOf(debut.matchId(), side), debut.matchId(), side, premiere.patch(),
                    premiere.queueId(), palier(nous), nous.getFirst().win(),
                    ecart(nous, eux, a15, MatchInsights.At15::gold),
                    ecart(nous, eux, a15, MatchInsights.At15::xp),
                    ecart(nous, eux, a15, MatchInsights.At15::kills),
                    objectifs == null ? 0 : objectifs.dragons(),
                    objectifs == null ? 0 : objectifs.grubs(),
                    objectifs == null ? 0 : objectifs.heralds(),
                    early == null ? 0 : (int) early.ganks().stream()
                            .filter(g -> g.attackerSide() == side && g.decisive()).count(),
                    early == null ? 0 : (int) early.ganks().stream()
                            .filter(g -> g.attackerSide() != side && g.decisive()).count()));
        }
        return camps;
    }

    // Tous les chiffres à 15 min des deux camps, sinon rien : un écart sur quatre joueurs serait faux.
    private static Integer ecart(List<MatchParticipation> nous, List<MatchParticipation> eux,
                                 Map<String, MatchInsights.At15> a15,
                                 ToIntFunction<MatchInsights.At15> valeur) {
        Integer a = somme(nous, a15, valeur);
        Integer b = somme(eux, a15, valeur);
        return a == null || b == null ? null : a - b;
    }

    private static Integer somme(List<MatchParticipation> camp, Map<String, MatchInsights.At15> a15,
                                 ToIntFunction<MatchInsights.At15> valeur) {
        int total = 0;
        for (MatchParticipation ligne : camp) {
            MatchInsights.At15 chiffres = a15.get(ligne.puuid());
            if (chiffres == null) {
                return null;
            }
            total += valeur.applyAsInt(chiffres);
        }
        return total;
    }

    static String palier(List<MatchParticipation> camp) {
        OptionalDouble moyenne = camp.stream()
                .map(MatchParticipation::rank)
                .filter(Objects::nonNull)
                .map(rang -> ReferenceService.groupe(rang.tier()))
                .filter(Objects::nonNull)
                .mapToInt(ReferenceService.PALIERS::indexOf)
                .filter(index -> index >= 0)
                .average();
        return moyenne.isPresent() ? ReferenceService.PALIERS.get((int) Math.round(moyenne.getAsDouble())) : null;
    }
}
