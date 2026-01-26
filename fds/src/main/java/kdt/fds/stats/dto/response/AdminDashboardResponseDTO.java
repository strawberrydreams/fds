package kdt.fds.stats.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 이 파일은 관리자 대시보드 응답 DTO 레코드 파일이다.
 * 주간 집계 통계와 분포 지표를 섹션별로 묶어 반환한다.
 */
public record AdminDashboardResponseDTO(
        DateRangeDTO range,
        UsersSectionDTO users,
        AccountsSectionDTO accounts,
        CardsSectionDTO cards,
        TransactionsSectionDTO transactions,
        TransactionFeaturesSectionDTO transactionFeatures,
        DetectionSectionDTO detections,
        FraudReportsSectionDTO fraudReports,
        BlacklistSectionDTO blacklist,
        ReferenceDataSectionDTO referenceData,
        CrossEntitySectionDTO crossEntity
) {
    public record DateRangeDTO(
            LocalDate fromDate,
            LocalDate toDate
    ) { }

    public record DateCountDTO(
            LocalDate date,
            long count
    ) { }

    public record NamedCountDTO(
            String name,
            long count
    ) { }

    public record NamedAmountDTO(
            String name,
            BigDecimal amount
    ) { }

    public record SegmentAverageDTO(
            String gender,
            String ageGroup,
            BigDecimal averageBalance
    ) { }

    public record AmountSummaryDTO(
            BigDecimal total,
            BigDecimal average
    ) { }

    public record FieldStatsDTO(
            long totalCount,
            long missingCount,
            BigDecimal missingRate,
            List<NamedCountDTO> topValues
    ) { }

    public record NumericSummaryDTO(
            String name,
            BigDecimal min,
            BigDecimal max,
            BigDecimal average
    ) { }

    /**
     * 사용자 섹션 DTO.
     * User 엔티티에 CREATED_AT이 없어 newUsersTrend는 제거됨.
     */
    public record UsersSectionDTO(
            long totalUsers,
            Map<String, Long> genderDistribution,
            Map<String, Long> ageDistribution
    ) { }

    public record AccountsSectionDTO(
            long totalAccounts,
            List<DateCountDTO> newAccountsTrend,
            Map<String, Long> statusDistribution,
            List<SegmentAverageDTO> averageBalanceByGenderAge,
            Map<String, Long> accountsPerUserDistribution
    ) { }

    public record CardsSectionDTO(
            long totalCards,
            List<DateCountDTO> newCardsTrend,
            Map<String, Long> statusDistribution,
            Map<String, Long> typeDistribution,
            Map<String, Long> issuerDistribution,
            Map<String, Long> cardsPerUserDistribution,
            Map<String, Long> cardsPerAccountDistribution
    ) { }

    /**
     * 거래 섹션 DTO.
     * balanceAfterTxSummary: 거래 후 잔액 통계 추가.
     * sourceValueStats: 출금 소스(계좌/카드) 통계 추가.
     */
    public record TransactionsSectionDTO(
            long totalTransactions,
            List<DateCountDTO> dailyTrend,
            Map<String, Long> hourlyDistribution,
            AmountSummaryDTO amountSummary,
            AmountSummaryDTO balanceAfterTxSummary,
            Map<String, Long> typeDistribution,
            FieldStatsDTO merchantCategoryStats,
            FieldStatsDTO locationStats,
            FieldStatsDTO targetAccountStats,
            FieldStatsDTO descriptionStats,
            FieldStatsDTO sourceValueStats,
            List<NamedCountDTO> topAccountsByCount,
            List<NamedAmountDTO> topAccountsByAmount,
            List<NamedCountDTO> topUsersByCount,
            List<NamedAmountDTO> topUsersByAmount
    ) { }

    public record TransactionFeaturesSectionDTO(
            long transactionCount,
            long featureCount,
            BigDecimal coverageRate,
            List<NumericSummaryDTO> balanceSummaries,
            long vFeaturesCount,
            BigDecimal averageFeaturesLength
    ) { }

    /**
     * 탐지 섹션 DTO.
     * FraudDetectionResult에 ACTION_TAKEN이 없어 actionDistribution 제거됨.
     */
    public record DetectionSectionDTO(
            long detectionCount,
            List<DateCountDTO> detectionTrend,
            BigDecimal detectionCoverage,
            BigDecimal averageDetectionDelayMinutes,
            long fraudCount,
            BigDecimal fraudRate,
            Map<String, Long> fraudProbabilityDistribution,
            Map<String, Long> engineDistribution,
            Map<String, Long> thresholdDistribution,
            BigDecimal thresholdExceedRate
    ) { }

    /**
     * 신고 섹션 DTO.
     * reasonCodeDistribution: 신고 사유 코드 분포 추가.
     * averageReportCount: 평균 중복 신고 횟수 추가.
     */
    public record FraudReportsSectionDTO(
            long totalReports,
            List<DateCountDTO> reportTrend,
            Map<String, Long> statusDistribution,
            List<NamedCountDTO> reasonTop,
            Map<String, Long> reasonCodeDistribution,
            BigDecimal averageReportCount,
            long distinctAccountCount,
            BigDecimal duplicateRate,
            List<NamedCountDTO> topAccounts,
            BigDecimal reportedAccountDetectionRate,
            BigDecimal reportedAccountFraudRate
    ) { }

    public record BlacklistSectionDTO(
            long totalBlacklist,
            List<DateCountDTO> newBlacklistTrend,
            Map<String, Long> reasonDistribution,
            long distinctAccountCount,
            long duplicateCount,
            long relatedTransactionCount,
            long relatedDetectionCount
    ) { }

    public record ReferenceDataSectionDTO(
            long codebookCount,
            List<DateCountDTO> codebookCreatedTrend,
            List<DateCountDTO> codebookUpdatedTrend,
            Map<String, Long> codeTypeDistribution,
            long activeCount,
            long inactiveCount,
            long metaJsonCount,
            long descriptionMissingCount,
            Map<String, Long> sortOrderDistribution,
            List<ConfigEntryDTO> configEntries
    ) { }

    public record ConfigEntryDTO(
            String configKey,
            String configValue,
            String description
    ) { }

    public record SegmentMetricDTO(
            String segmentType,
            String segmentValue,
            long transactionCount,
            long detectedCount,
            long fraudCount
    ) { }

    public record FraudBucketDTO(
            String bucket,
            long transactionCount,
            long fraudCount,
            BigDecimal fraudRate,
            Double averageFraudProbability
    ) { }

    /**
     * 탐지 엔진별 비교 DTO.
     * FraudDetectionResult에 ACTION_TAKEN이 없어 action 필드 제거됨.
     */
    public record EngineComparisonDTO(
            String engine,
            Double averageFraudProbability,
            BigDecimal fraudRate,
            long totalCount
    ) { }

    public record AccountRankDTO(
            String accountNumber,
            long transactionCount,
            long detectedCount,
            long fraudCount
    ) { }

    /**
     * 교차 분석 섹션 DTO.
     * engineActionComparisons -> engineComparisons로 변경 (ACTION_TAKEN 제거).
     */
    public record CrossEntitySectionDTO(
            List<SegmentMetricDTO> segmentMetrics,
            List<AccountRankDTO> accountRanking,
            Map<String, Long> merchantCategoryBreakdown,
            Map<String, Long> locationBreakdown,
            Map<String, Long> targetAccountBreakdown,
            Map<String, Long> transactionTypeBreakdown,
            List<FraudBucketDTO> amountBuckets,
            List<FraudBucketDTO> hourBuckets,
            List<FraudBucketDTO> typeBuckets,
            List<EngineComparisonDTO> engineComparisons,
            BigDecimal blacklistDetectionRate
    ) { }
}
