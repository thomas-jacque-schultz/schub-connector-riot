package schultz.thomas.schub.connector.riot.business.ingest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import schultz.thomas.schub.connector.riot.api.dto.SamplingStatus;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.mapper.RiotStatsMapper;
import schultz.thomas.schub.connector.riot.business.services.RankHistory;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskState;
import schultz.thomas.schub.connector.riot.data.model.IngestTaskType;
import schultz.thomas.schub.connector.riot.data.model.LadderBound;
import schultz.thomas.schub.connector.riot.data.model.LadderSeed;
import schultz.thomas.schub.connector.riot.data.model.MatchLobby;
import schultz.thomas.schub.connector.riot.data.model.riot.RiotLeagueEntryResponse;
import schultz.thomas.schub.connector.riot.data.repository.IngestTaskRepository;
import schultz.thomas.schub.connector.riot.data.repository.LadderBoundRepository;
import schultz.thomas.schub.connector.riot.data.repository.LadderSeedRepository;
import schultz.thomas.schub.connector.riot.data.repository.MatchLobbyRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

// La collecte de fond suit les comptes croisés par l'équipe, donc son palier : ceci équilibre la population par palier.
@Slf4j
@Component
@RequiredArgsConstructor
public class LadderSampler {

    static final String QUEUE = "RANKED_SOLO_5x5";
    static final int QUEUE_ID = 420;
    static final String SOMMET = "MASTER_PLUS";
    static final List<String> PALIERS = List.of("IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND",
            SOMMET);
    private static final List<String> DIVISIONS = List.of("I", "II", "III", "IV");
    private static final List<String> LIGUES_DU_SOMMET = List.of("MASTER", "GRANDMASTER", "CHALLENGER");
    private static final List<IngestTaskState> EN_COURS = List.of(IngestTaskState.PENDING, IngestTaskState.RUNNING);

    private final RiotProperties properties;
    private final RiotApiClient riotApiClient;
    private final RiotStatsMapper mapper;
    private final RankHistory rankHistory;
    private final IngestQueue queue;
    private final IngestTaskRepository tasks;
    private final LadderSeedRepository seeds;
    private final LadderBoundRepository bounds;
    private final MatchLobbyRepository lobbies;
    private final BackgroundCrawler crawler;
    private final Clock clock;

    Random random = new Random();

    public void round() {
        if (!properties.getSampling().isEnabled() || !properties.getIngest().isEnabled() || !crawler.active()) {
            return;
        }
        for (String palier : PALIERS) {
            if (manque(palier) > 0
                    && tasks.countByTypeAndKeyStartingWithAndStateIn(IngestTaskType.LADDER_PAGE, palier + "/", EN_COURS) == 0) {
                queue.enqueue(IngestTaskType.LADDER_PAGE, tirage(palier), null, IngestTask.BACKGROUND_PLAYER_PRIORITY);
            }
        }
    }

    // Clé : GOLD/II/17, ou MASTER_PLUS/CHALLENGER. Tous les rangs lus nourrissent l'historique, graines ou pas.
    public void samplePage(String key) {
        String[] parts = key.split("/");
        String palier = parts[0];
        Instant maintenant = clock.instant();
        List<RiotLeagueEntryResponse> entrees = SOMMET.equals(palier)
                ? riotApiClient.apexLeague(QUEUE, parts[1])
                : riotApiClient.ladderPage(QUEUE, palier, parts[1], Integer.parseInt(parts[2]));

        rankHistory.recordAll(entrees.stream()
                .map(entree -> new RankHistory.Observation(entree.puuid(), mapper.toStanding(entree, maintenant)))
                .toList());
        if (entrees.isEmpty()) {
            if (!SOMMET.equals(palier)) {
                borne(palier + "/" + parts[1], Integer.parseInt(parts[2]));
            }
            return;
        }

        long manque = manque(palier);
        if (manque <= 0) {
            return;
        }
        Set<String> dejaGraines = new HashSet<>();
        seeds.findByPuuidInAndSampledAtGreaterThanEqual(entrees.stream().map(RiotLeagueEntryResponse::puuid).toList(),
                        debutFenetre())
                .forEach(seed -> dejaGraines.add(seed.puuid()));
        List<RiotLeagueEntryResponse> candidates = new ArrayList<>(entrees.stream()
                .filter(entree -> entree.puuid() != null && !entree.puuid().isBlank() && !entree.inactive())
                .filter(entree -> !dejaGraines.contains(entree.puuid()))
                .toList());
        Collections.shuffle(candidates, random);

        List<RiotLeagueEntryResponse> retenues = candidates.stream()
                .limit(Math.min(properties.getSampling().getSeedsPerPage(), manque))
                .toList();
        for (RiotLeagueEntryResponse entree : retenues) {
            seeds.save(new LadderSeed(entree.puuid(), palier, entree.tier(), entree.rank(), entree.leaguePoints(),
                    maintenant));
            queue.enqueue(IngestTaskType.SEED_MATCHES, entree.puuid(), entree.puuid(),
                    IngestTask.BACKGROUND_PLAYER_PRIORITY);
        }
        log.info("Échantillon {} : {} graines tirées de {}, {} manquantes.", palier, retenues.size(), key,
                manque - retenues.size());
    }

    // Seulement le classé solo : c'est la seule file où le rang d'une graine vaut pour toute la partie.
    public void collectSeed(String puuid) {
        LadderSeed seed = seeds.findById(puuid).orElse(null);
        if (seed == null) {
            return;
        }
        List<String> matchIds = riotApiClient.rankedMatchIds(puuid, QUEUE_ID, properties.getSampling().getGamesPerSeed());
        Set<String> connues = new HashSet<>();
        lobbies.findAllById(matchIds).forEach(lobby -> connues.add(lobby.matchId()));
        lobbies.saveAll(matchIds.stream()
                .filter(matchId -> !connues.contains(matchId))
                .map(matchId -> new MatchLobby(matchId, puuid, seed.tier(), seed.division(), seed.sampledAt()))
                .toList());
        matchIds.forEach(matchId -> queue.enqueue(IngestTaskType.MATCH_TIMELINE_DIGEST, matchId, puuid,
                IngestTask.backgroundPriority(IngestTask.sequenceOf(matchId))));
    }

    public SamplingStatus status() {
        int cible = properties.getSampling().getSeedsPerTier();
        return new SamplingStatus(properties.getSampling().getWindow(), PALIERS.stream()
                .map(palier -> new SamplingStatus.Tier(palier,
                        seeds.countByGroupAndSampledAtGreaterThanEqual(palier, debutFenetre()), cible))
                .toList(), lobbies.count());
    }

    private long manque(String palier) {
        return properties.getSampling().getSeedsPerTier()
                - seeds.countByGroupAndSampledAtGreaterThanEqual(palier, debutFenetre());
    }

    private String tirage(String palier) {
        if (SOMMET.equals(palier)) {
            return palier + "/" + LIGUES_DU_SOMMET.get(random.nextInt(LIGUES_DU_SOMMET.size()));
        }
        String division = DIVISIONS.get(random.nextInt(DIVISIONS.size()));
        int plafond = bounds.findById(palier + "/" + division)
                .map(bound -> bound.firstEmptyPage() - 1)
                .orElse(properties.getSampling().getMaxPage());
        return palier + "/" + division + "/" + (1 + random.nextInt(Math.max(1, plafond)));
    }

    private void borne(String division, int pageVide) {
        int connue = bounds.findById(division).map(LadderBound::firstEmptyPage).orElse(Integer.MAX_VALUE);
        if (pageVide < connue) {
            bounds.save(new LadderBound(division, pageVide));
        }
    }

    private Instant debutFenetre() {
        return clock.instant().minus(properties.getSampling().getWindow());
    }
}
