package schultz.thomas.schub.connector.riot.business.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import schultz.thomas.schub.connector.riot.api.dto.MatchDetail;
import schultz.thomas.schub.connector.riot.api.dto.MatchDetailsResponse;
import schultz.thomas.schub.connector.riot.business.client.RiotApiClient;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotKeyMissingException;
import schultz.thomas.schub.connector.riot.business.exceptions.RiotQuotaExceededException;
import schultz.thomas.schub.connector.riot.business.ingest.ParticipationProjector;
import schultz.thomas.schub.connector.riot.business.mapper.RawMatchDecoder;
import schultz.thomas.schub.connector.riot.config.RiotProperties;
import schultz.thomas.schub.connector.riot.data.model.CachedMatch;
import schultz.thomas.schub.connector.riot.data.model.PlayerMatchRef;
import schultz.thomas.schub.connector.riot.data.repository.CachedMatchRepository;
import schultz.thomas.schub.connector.riot.data.repository.PlayerMatchRefRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
public class MatchDetailService {

    private final CachedMatchRepository matches;
    private final PlayerMatchRefRepository playerMatches;
    private final RiotApiClient riotApiClient;
    private final RawMatchDecoder decoder;
    private final ParticipationProjector projector;
    private final RiotProperties properties;
    private final Clock clock;

    public Optional<MatchDetail> detail(String matchId) {
        Optional<CachedMatch> cached = matches.findById(matchId);
        if (cached.isPresent()) {
            return Optional.of(decoder.toDetail(cached.get().raw()));
        }
        return fetchAndStore(matchId);
    }

    public MatchDetailsResponse details(List<String> matchIds) {
        Set<String> requested = new LinkedHashSet<>(matchIds);
        List<MatchDetail> found = new ArrayList<>(matches.findByMatchIdIn(requested).stream()
                .map(cached -> decoder.toDetail(cached.raw()))
                .toList());

        Set<String> known = found.stream().map(MatchDetail::matchId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<String> pending = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        int budget = properties.getCache().getMaxDetailsPerCall();

        for (String matchId : requested) {
            if (known.contains(matchId)) {
                continue;
            }
            if (budget <= 0) {
                pending.add(matchId);
                continue;
            }
            budget--;
            try {
                fetchAndStore(matchId).ifPresentOrElse(found::add, () -> unavailable.add(matchId));
            } catch (RiotQuotaExceededException | RiotKeyMissingException interrupted) {
                log.info("Récupération des détails interrompue : {}", interrupted.getMessage());
                pending.add(matchId);
                budget = 0;
            }
        }

        found.sort(Comparator.comparing(MatchDetail::startedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return new MatchDetailsResponse(List.copyOf(found), List.copyOf(pending), List.copyOf(unavailable));
    }

    private Optional<MatchDetail> fetchAndStore(String matchId) {
        return riotApiClient.match(matchId).map(raw -> {
            MatchDetail detail = decoder.toDetail(raw);
            matches.save(new CachedMatch(detail.matchId(), raw, clock.instant()));
            projector.project(detail);
            stampReferences(detail);
            return detail;
        });
    }

    private void stampReferences(MatchDetail detail) {
        List<PlayerMatchRef> undated = playerMatches.findByMatchIdIn(List.of(detail.matchId())).stream()
                .filter(reference -> reference.playedAt() == null)
                .map(reference -> reference.playedAt(detail.startedAt()))
                .toList();
        if (!undated.isEmpty()) {
            playerMatches.saveAll(undated);
        }
    }
}
