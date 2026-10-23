package schultz.thomas.schub.connector.riot.api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import schultz.thomas.schub.connector.riot.api.dto.AccountsByRankDto;
import schultz.thomas.schub.connector.riot.api.dto.ChampionCatalogDto;
import schultz.thomas.schub.connector.riot.api.dto.ChampionComboDto;
import schultz.thomas.schub.connector.riot.api.dto.ChampionCombosQueryDto;
import schultz.thomas.schub.connector.riot.api.dto.ChampionMasteryDto;
import schultz.thomas.schub.connector.riot.api.dto.ChampionReferenceGridDto;
import schultz.thomas.schub.connector.riot.api.dto.CrawlerStatusDto;
import schultz.thomas.schub.connector.riot.api.dto.EarlyGameDto;
import schultz.thomas.schub.connector.riot.api.dto.HistorySyncReportDto;
import schultz.thomas.schub.connector.riot.api.dto.HistoryWindowDto;
import schultz.thomas.schub.connector.riot.api.dto.IndexablePlayerDto;
import schultz.thomas.schub.connector.riot.api.dto.IngestEnqueueReportDto;
import schultz.thomas.schub.connector.riot.api.dto.IngestPauseStatusDto;
import schultz.thomas.schub.connector.riot.api.dto.IngestStatusDto;
import schultz.thomas.schub.connector.riot.api.dto.IngestSummaryDto;
import schultz.thomas.schub.connector.riot.api.dto.IngestTaskDto;
import schultz.thomas.schub.connector.riot.api.dto.KnownAccountRebuildReportDto;
import schultz.thomas.schub.connector.riot.api.dto.MatchDetailDto;
import schultz.thomas.schub.connector.riot.api.dto.MatchDetailsResponseDto;
import schultz.thomas.schub.connector.riot.api.dto.MatchHistoryDto;
import schultz.thomas.schub.connector.riot.api.dto.MatchInsightsDto;
import schultz.thomas.schub.connector.riot.api.dto.MatchPlayerMetricsDto;
import schultz.thomas.schub.connector.riot.api.dto.ParticipationBucketDto;
import schultz.thomas.schub.connector.riot.api.dto.PatchStartDto;
import schultz.thomas.schub.connector.riot.api.dto.PlayerCoverageDto;
import schultz.thomas.schub.connector.riot.api.dto.PlayerIdentityDto;
import schultz.thomas.schub.connector.riot.api.dto.PlayerIngestStatusDto;
import schultz.thomas.schub.connector.riot.api.dto.PlayerReferencesDto;
import schultz.thomas.schub.connector.riot.api.dto.PlayerSuggestionDto;
import schultz.thomas.schub.connector.riot.api.dto.RankedStandingDto;
import schultz.thomas.schub.connector.riot.api.dto.RebuildReportDto;
import schultz.thomas.schub.connector.riot.api.dto.ReferenceGridDto;
import schultz.thomas.schub.connector.riot.api.dto.ReferencesQueryDto;
import schultz.thomas.schub.connector.riot.api.dto.RiotDataInventoryDto;
import schultz.thomas.schub.connector.riot.api.dto.RiotDataPurgeReportDto;
import schultz.thomas.schub.connector.riot.api.dto.SamplingStatusDto;
import schultz.thomas.schub.connector.riot.api.dto.SeoCreatorsDto;
import schultz.thomas.schub.connector.riot.api.dto.SharedMatchesDto;
import schultz.thomas.schub.connector.riot.api.dto.TierCombosDto;
import schultz.thomas.schub.connector.riot.api.dto.TimelineHabitsDto;
import schultz.thomas.schub.connector.riot.business.model.AccountsByRank;
import schultz.thomas.schub.connector.riot.business.model.ChampionCombo;
import schultz.thomas.schub.connector.riot.business.model.ChampionCombosQuery;
import schultz.thomas.schub.connector.riot.business.model.ChampionReferenceGrid;
import schultz.thomas.schub.connector.riot.business.model.CrawlerStatus;
import schultz.thomas.schub.connector.riot.business.model.HistorySyncReport;
import schultz.thomas.schub.connector.riot.business.model.HistoryWindow;
import schultz.thomas.schub.connector.riot.business.model.IndexablePlayer;
import schultz.thomas.schub.connector.riot.business.model.IngestEnqueueReport;
import schultz.thomas.schub.connector.riot.business.model.IngestPauseStatus;
import schultz.thomas.schub.connector.riot.business.model.IngestStatus;
import schultz.thomas.schub.connector.riot.business.model.IngestSummary;
import schultz.thomas.schub.connector.riot.business.model.KnownAccountRebuildReport;
import schultz.thomas.schub.connector.riot.business.model.MatchDetail;
import schultz.thomas.schub.connector.riot.business.model.MatchDetailsResponse;
import schultz.thomas.schub.connector.riot.business.model.MatchHistory;
import schultz.thomas.schub.connector.riot.business.model.MatchPlayerMetrics;
import schultz.thomas.schub.connector.riot.business.model.ParticipationBucket;
import schultz.thomas.schub.connector.riot.business.model.PatchStart;
import schultz.thomas.schub.connector.riot.business.model.PlayerCoverage;
import schultz.thomas.schub.connector.riot.business.model.PlayerIdentity;
import schultz.thomas.schub.connector.riot.business.model.PlayerIngestStatus;
import schultz.thomas.schub.connector.riot.business.model.PlayerReferences;
import schultz.thomas.schub.connector.riot.business.model.PlayerSuggestion;
import schultz.thomas.schub.connector.riot.business.model.RebuildReport;
import schultz.thomas.schub.connector.riot.business.model.ReferenceGrid;
import schultz.thomas.schub.connector.riot.business.model.ReferencesQuery;
import schultz.thomas.schub.connector.riot.business.model.RiotDataInventory;
import schultz.thomas.schub.connector.riot.business.model.RiotDataPurgeReport;
import schultz.thomas.schub.connector.riot.business.model.SamplingStatus;
import schultz.thomas.schub.connector.riot.business.model.SeoCreators;
import schultz.thomas.schub.connector.riot.business.model.SharedMatches;
import schultz.thomas.schub.connector.riot.business.model.TierCombos;
import schultz.thomas.schub.connector.riot.business.model.TimelineHabits;
import schultz.thomas.schub.connector.riot.data.model.ChampionCatalog;
import schultz.thomas.schub.connector.riot.data.model.ChampionMastery;
import schultz.thomas.schub.connector.riot.data.model.EarlyGame;
import schultz.thomas.schub.connector.riot.data.model.IngestTask;
import schultz.thomas.schub.connector.riot.data.model.MatchInsights;
import schultz.thomas.schub.connector.riot.data.model.RankedStanding;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RiotDtoMapper {

    @Mapping(target = "decisive", expression = "java(gank.decisive())")
    EarlyGameDto.Gank toDto(EarlyGame.Gank gank);

    default List<EarlyGameDto.Proximity> toDto(EarlyGame.Proximity proximity) {
        if (proximity == null) {
            return null;
        }
        List<String> puuids = proximity.puuids();
        return proximity.pairs().stream()
                .map(paire -> new EarlyGameDto.Proximity(puuids.get(paire.a()), puuids.get(paire.b()), paire.early(),
                        paire.earlyTogether(), paire.late(), paire.lateTogether(), paire.meanDistance(),
                        paire.departures()))
                .toList();
    }

    AccountsByRankDto toDto(AccountsByRank value);

    ChampionCatalogDto toDto(ChampionCatalog value);

    ChampionReferenceGridDto toDto(ChampionReferenceGrid value);

    CrawlerStatusDto toDto(CrawlerStatus value);

    HistorySyncReportDto toDto(HistorySyncReport value);

    HistoryWindowDto toDto(HistoryWindow value);

    IngestEnqueueReportDto toDto(IngestEnqueueReport value);

    IngestPauseStatusDto toDto(IngestPauseStatus value);

    IngestStatusDto toDto(IngestStatus value);

    IngestSummaryDto toDto(IngestSummary value);

    KnownAccountRebuildReportDto toDto(KnownAccountRebuildReport value);

    MatchDetailDto toDto(MatchDetail value);

    MatchDetailsResponseDto toDto(MatchDetailsResponse value);

    MatchHistoryDto toDto(MatchHistory value);

    PlayerIdentityDto toDto(PlayerIdentity value);

    PlayerIngestStatusDto toDto(PlayerIngestStatus value);

    RebuildReportDto toDto(RebuildReport value);

    ReferenceGridDto toDto(ReferenceGrid value);

    TierCombosDto toDto(TierCombos value);

    RiotDataInventoryDto toDto(RiotDataInventory value);

    RiotDataPurgeReportDto toDto(RiotDataPurgeReport value);

    SamplingStatusDto toDto(SamplingStatus value);

    SeoCreatorsDto toDto(SeoCreators value);

    SharedMatchesDto toDto(SharedMatches value);

    TimelineHabitsDto toDto(TimelineHabits value);

    List<ChampionComboDto> toChampionComboDtos(List<ChampionCombo> values);

    List<ChampionMasteryDto> toChampionMasteryDtos(List<ChampionMastery> values);

    List<IndexablePlayerDto> toIndexablePlayerDtos(List<IndexablePlayer> values);

    List<IngestTaskDto> toIngestTaskDtos(List<IngestTask> values);

    List<MatchInsightsDto> toMatchInsightsDtos(List<MatchInsights> values);

    List<MatchPlayerMetricsDto> toMatchPlayerMetricsDtos(List<MatchPlayerMetrics> values);

    List<ParticipationBucketDto> toParticipationBucketDtos(List<ParticipationBucket> values);

    List<PatchStartDto> toPatchStartDtos(List<PatchStart> values);

    List<PlayerCoverageDto> toPlayerCoverageDtos(List<PlayerCoverage> values);

    List<PlayerReferencesDto> toPlayerReferencesDtos(List<PlayerReferences> values);

    List<PlayerSuggestionDto> toPlayerSuggestionDtos(List<PlayerSuggestion> values);

    List<RankedStandingDto> toRankedStandingDtos(List<RankedStanding> values);

    ChampionCombosQuery fromDto(ChampionCombosQueryDto value);

    HistoryWindow fromDto(HistoryWindowDto value);

    ReferencesQuery fromDto(ReferencesQueryDto value);
}
