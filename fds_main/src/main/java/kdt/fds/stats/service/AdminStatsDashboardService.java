package kdt.fds.stats.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kdt.fds.stats.dto.response.AdminDashboardResponseDTO;
import kdt.fds.stats.vo.StatsDateRange;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 대시보드에 필요한 집계를 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class AdminStatsDashboardService extends StatsDashboardSupport {
    public AdminStatsDashboardService(NamedParameterJdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    /**
     * 관리자 대시보드에 필요한 모든 섹션 통계를 지정 기간 기준으로 집계한다. 관리자 대시보드 응답에 사용될 DTO 객체를 반환한다.
     */
    public AdminDashboardResponseDTO getAdminDashboard(LocalDate fromDate, LocalDate toDate) {
        StatsDateRange range = resolveRange(fromDate, toDate);
        MapSqlParameterSource rangeParams = new MapSqlParameterSource()
                .addValue("fromTs", range.fromTimestamp())
                .addValue("toTs", range.toExclusiveTimestamp());

        AdminDashboardResponseDTO.UsersSectionDTO users = buildUsersSection(rangeParams);
        AdminDashboardResponseDTO.AccountsSectionDTO accounts = buildAccountsSection(rangeParams);
        AdminDashboardResponseDTO.CardsSectionDTO cards = buildCardsSection(rangeParams);
        AdminDashboardResponseDTO.TransactionsSectionDTO transactions = buildTransactionsSection(rangeParams);
        AdminDashboardResponseDTO.TransactionFeaturesSectionDTO transactionFeatures = buildTransactionFeaturesSection();
        AdminDashboardResponseDTO.DetectionSectionDTO detections =
                buildDetectionSection(rangeParams, transactions.totalTransactions());
        AdminDashboardResponseDTO.FraudReportsSectionDTO fraudReports = buildFraudReportsSection(rangeParams);
        AdminDashboardResponseDTO.BlacklistSectionDTO blacklist = buildBlacklistSection(rangeParams);
        AdminDashboardResponseDTO.ReferenceDataSectionDTO referenceData = buildReferenceDataSection(rangeParams);
        AdminDashboardResponseDTO.CrossEntitySectionDTO crossEntity =
                buildCrossEntitySection(rangeParams);

        return new AdminDashboardResponseDTO(
                new AdminDashboardResponseDTO.DateRangeDTO(range.fromDate(), range.toDate()),
                users,
                accounts,
                cards,
                transactions,
                transactionFeatures,
                detections,
                fraudReports,
                blacklist,
                referenceData,
                crossEntity
        );
    }

    /**
     * 사용자 섹션 통계를 구성한다. 사용자 섹션 응답에 사용될 DTO 객체를 반환한다.
     */
    private AdminDashboardResponseDTO.UsersSectionDTO buildUsersSection(MapSqlParameterSource rangeParams) {
        long totalUsers = queryLong("SELECT COUNT(*) FROM USERS", new MapSqlParameterSource());
        Map<String, Long> genderDistribution = loadDistribution("""
                SELECT NVL(GENDER, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM USERS
                GROUP BY NVL(GENDER, 'UNKNOWN')
                """, new MapSqlParameterSource());
        Map<String, Long> ageDistribution = computeAgeDistribution();

        return new AdminDashboardResponseDTO.UsersSectionDTO(
                totalUsers,
                genderDistribution,
                ageDistribution
        );
    }

    /**
     * 계좌 섹션 통계를 구성한다. 계좌 섹션 응답에 사용될 DTO 객체를 반환한다.
     */
    private AdminDashboardResponseDTO.AccountsSectionDTO buildAccountsSection(MapSqlParameterSource rangeParams) {
        long totalAccounts = queryLong("SELECT COUNT(*) FROM ACCOUNTS", new MapSqlParameterSource());
        List<AdminDashboardResponseDTO.DateCountDTO> newAccountsTrend = loadAdminDateCounts("""
                SELECT TRUNC(CREATED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE
                FROM ACCOUNTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY TRUNC(CREATED_AT)
                ORDER BY TRUNC(CREATED_AT)
                """, rangeParams);
        Map<String, Long> statusDistribution = loadDistribution("""
                SELECT NVL(STATUS, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM ACCOUNTS
                GROUP BY NVL(STATUS, 'UNKNOWN')
                """, new MapSqlParameterSource());
        Map<String, String> accountStatusLabels = loadCodebookLabels("ACCOUNT_STATUS");
        Set<String> accountStatusDuplicates = findDuplicateLabels(accountStatusLabels);
        statusDistribution = mapDistributionLabels(statusDistribution, accountStatusLabels, accountStatusDuplicates);
        List<AdminDashboardResponseDTO.SegmentAverageDTO> averageBalanceByGenderAge =
                computeAverageBalanceByGenderAge();
        Map<String, Long> accountsPerUserDistribution = computeCountDistribution("""
                SELECT USER_INNER_ID AS OWNER_ID, COUNT(*) AS COUNT_VALUE
                FROM ACCOUNTS
                GROUP BY USER_INNER_ID
                """);

        return new AdminDashboardResponseDTO.AccountsSectionDTO(
                totalAccounts,
                newAccountsTrend,
                statusDistribution,
                averageBalanceByGenderAge,
                accountsPerUserDistribution
        );
    }

    /**
     * 카드 섹션 통계를 구성한다. 카드 섹션 응답에 사용될 DTO 객체를 반환한다.
     */
    private AdminDashboardResponseDTO.CardsSectionDTO buildCardsSection(MapSqlParameterSource rangeParams) {
        long totalCards = queryLong("SELECT COUNT(*) FROM CARDS", new MapSqlParameterSource());
        List<AdminDashboardResponseDTO.DateCountDTO> newCardsTrend = loadAdminDateCounts("""
                SELECT TRUNC(CREATED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE
                FROM CARDS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY TRUNC(CREATED_AT)
                ORDER BY TRUNC(CREATED_AT)
                """, rangeParams);
        Map<String, Long> statusDistribution = loadDistribution("""
                SELECT NVL(STATUS, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM CARDS
                GROUP BY NVL(STATUS, 'UNKNOWN')
                """, new MapSqlParameterSource());
        Map<String, Long> typeDistribution = loadDistribution("""
                SELECT NVL(CARD_TYPE, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM CARDS
                GROUP BY NVL(CARD_TYPE, 'UNKNOWN')
                """, new MapSqlParameterSource());
        Map<String, String> cardStatusLabels = loadCodebookLabels("CARD_STATUS");
        Set<String> cardStatusDuplicates = findDuplicateLabels(cardStatusLabels);
        statusDistribution = mapDistributionLabels(statusDistribution, cardStatusLabels, cardStatusDuplicates);
        Map<String, String> cardTypeLabels = loadCodebookLabels("CARD_TYPE");
        Set<String> cardTypeDuplicates = findDuplicateLabels(cardTypeLabels);
        typeDistribution = mapDistributionLabels(typeDistribution, cardTypeLabels, cardTypeDuplicates);
        Map<String, Long> issuerDistribution = loadDistribution("""
                SELECT NVL(ISSUER, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM CARDS
                GROUP BY NVL(ISSUER, 'UNKNOWN')
                """, new MapSqlParameterSource());
        Map<String, Long> cardsPerUserDistribution = computeCountDistribution("""
                SELECT USER_INNER_ID AS OWNER_ID, COUNT(*) AS COUNT_VALUE
                FROM CARDS
                GROUP BY USER_INNER_ID
                """);
        Map<String, Long> cardsPerAccountDistribution = computeCountDistribution("""
                SELECT ACCOUNT_ID AS OWNER_ID, COUNT(*) AS COUNT_VALUE
                FROM CARDS
                GROUP BY ACCOUNT_ID
                """);

        return new AdminDashboardResponseDTO.CardsSectionDTO(
                totalCards,
                newCardsTrend,
                statusDistribution,
                typeDistribution,
                issuerDistribution,
                cardsPerUserDistribution,
                cardsPerAccountDistribution
        );
    }

    /**
     * 거래 섹션 통계를 구성한다. 거래 섹션 응답에 사용될 DTO 객체를 반환한다.
     */
    private AdminDashboardResponseDTO.TransactionsSectionDTO buildTransactionsSection(MapSqlParameterSource rangeParams) {
        long totalTransactions = queryLong("""
                SELECT COUNT(*)
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                """, rangeParams);
        List<AdminDashboardResponseDTO.DateCountDTO> dailyTrend = loadAdminDateCounts("""
                SELECT TRUNC(CREATED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY TRUNC(CREATED_AT)
                ORDER BY TRUNC(CREATED_AT)
                """, rangeParams);
        Map<String, Long> hourlyDistribution = loadDistribution("""
                SELECT TO_CHAR(EXTRACT(HOUR FROM CREATED_AT)) AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY EXTRACT(HOUR FROM CREATED_AT)
                ORDER BY EXTRACT(HOUR FROM CREATED_AT)
                """, rangeParams);
        BigDecimal totalAmount = queryDecimal("""
                SELECT NVL(SUM(TX_AMOUNT), 0)
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                """, rangeParams);
        BigDecimal averageAmount = queryDecimal("""
                SELECT AVG(TX_AMOUNT)
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                """, rangeParams);
        BigDecimal totalBalanceAfterTx = queryDecimal("""
                SELECT NVL(SUM(BALANCE_AFTER_TX), 0)
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                  AND BALANCE_AFTER_TX IS NOT NULL
                """, rangeParams);
        BigDecimal averageBalanceAfterTx = queryDecimal("""
                SELECT AVG(BALANCE_AFTER_TX)
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                  AND BALANCE_AFTER_TX IS NOT NULL
                """, rangeParams);
        Map<String, Long> typeDistribution = loadDistribution("""
                SELECT NVL(TX_TYPE, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY NVL(TX_TYPE, 'UNKNOWN')
                """, rangeParams);
        Map<String, String> transactionTypeLabels = loadCodebookLabels("TRANSACTION_TYPE");
        Set<String> transactionTypeDuplicates = findDuplicateLabels(transactionTypeLabels);
        typeDistribution = mapDistributionLabels(typeDistribution, transactionTypeLabels, transactionTypeDuplicates);

        AdminDashboardResponseDTO.FieldStatsDTO merchantCategoryStats =
                buildFieldStats("MERCHANT_CAT", rangeParams);
        AdminDashboardResponseDTO.FieldStatsDTO locationStats =
                buildFieldStats("LOCATION", rangeParams);
        AdminDashboardResponseDTO.FieldStatsDTO targetAccountStats =
                buildFieldStats("TARGET_ACCOUNT_NUMBER", rangeParams);
        AdminDashboardResponseDTO.FieldStatsDTO descriptionStats =
                buildFieldStats("DESCRIPTION", rangeParams);
        AdminDashboardResponseDTO.FieldStatsDTO sourceValueStats =
                buildFieldStats("SOURCE_VALUE", rangeParams);

        List<AdminDashboardResponseDTO.NamedCountDTO> topAccountsByCount = loadNamedCounts("""
                SELECT a.ACCOUNT_NUMBER AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                GROUP BY a.ACCOUNT_NUMBER
                ORDER BY COUNT(*) DESC
                FETCH NEXT :limit ROWS ONLY
                """, rangeParams);
        List<AdminDashboardResponseDTO.NamedAmountDTO> topAccountsByAmount = loadNamedAmounts("""
                SELECT a.ACCOUNT_NUMBER AS KEY_NAME, SUM(t.TX_AMOUNT) AS AMOUNT_VALUE
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                GROUP BY a.ACCOUNT_NUMBER
                ORDER BY SUM(t.TX_AMOUNT) DESC
                FETCH NEXT :limit ROWS ONLY
                """, rangeParams);
        List<AdminDashboardResponseDTO.NamedCountDTO> topUsersByCount = loadNamedCounts("""
                SELECT u.USER_ID AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN USERS u ON u.ID = a.USER_INNER_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                GROUP BY u.USER_ID
                ORDER BY COUNT(*) DESC
                FETCH NEXT :limit ROWS ONLY
                """, rangeParams);
        List<AdminDashboardResponseDTO.NamedAmountDTO> topUsersByAmount = loadNamedAmounts("""
                SELECT u.USER_ID AS KEY_NAME, SUM(t.TX_AMOUNT) AS AMOUNT_VALUE
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN USERS u ON u.ID = a.USER_INNER_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                GROUP BY u.USER_ID
                ORDER BY SUM(t.TX_AMOUNT) DESC
                FETCH NEXT :limit ROWS ONLY
                """, rangeParams);

        return new AdminDashboardResponseDTO.TransactionsSectionDTO(
                totalTransactions,
                dailyTrend,
                hourlyDistribution,
                new AdminDashboardResponseDTO.AmountSummaryDTO(totalAmount, averageAmount),
                new AdminDashboardResponseDTO.AmountSummaryDTO(totalBalanceAfterTx, averageBalanceAfterTx),
                typeDistribution,
                merchantCategoryStats,
                locationStats,
                targetAccountStats,
                descriptionStats,
                sourceValueStats,
                topAccountsByCount,
                topAccountsByAmount,
                topUsersByCount,
                topUsersByAmount
        );
    }

    /**
     * 거래 피처 섹션 통계를 구성한다. 거래 피처 섹션 응답에 사용될 DTO 객체를 반환한다.
     */
    private AdminDashboardResponseDTO.TransactionFeaturesSectionDTO buildTransactionFeaturesSection() {
        long transactionCount = queryLong("SELECT COUNT(*) FROM TRANSACTIONS", new MapSqlParameterSource());
        long featureCount = queryLong("SELECT COUNT(*) FROM TRANSACTION_FEATURES", new MapSqlParameterSource());
        BigDecimal coverageRate = safeRate(featureCount, transactionCount);
        List<AdminDashboardResponseDTO.NumericSummaryDTO> balanceSummaries = List.of(
                buildNumericSummary("OLD_BALANCE_ORG"),
                buildNumericSummary("NEW_BALANCE_ORG")
        );
        long vFeaturesCount = queryLong("""
                SELECT COUNT(*)
                FROM TRANSACTION_FEATURES
                WHERE V_FEATURES IS NOT NULL
                """, new MapSqlParameterSource());
        BigDecimal avgFeaturesLength = queryDecimal("""
                SELECT AVG(LENGTH(V_FEATURES))
                FROM TRANSACTION_FEATURES
                WHERE V_FEATURES IS NOT NULL
                """, new MapSqlParameterSource());

        return new AdminDashboardResponseDTO.TransactionFeaturesSectionDTO(
                transactionCount,
                featureCount,
                coverageRate,
                balanceSummaries,
                vFeaturesCount,
                avgFeaturesLength
        );
    }

    /**
     * 탐지 섹션 통계를 구성한다. 탐지 섹션 응답에 사용될 DTO 객체를 반환한다.
     */
    private AdminDashboardResponseDTO.DetectionSectionDTO buildDetectionSection(
            MapSqlParameterSource rangeParams,
            long transactionCount
    ) {
        long detectionCount = queryLong("""
                SELECT COUNT(*)
                FROM FRAUD_DETECTION_RESULTS
                WHERE DETECTED_AT >= :fromTs AND DETECTED_AT < :toTs
                """, rangeParams);
        List<AdminDashboardResponseDTO.DateCountDTO> detectionTrend = loadAdminDateCounts("""
                SELECT TRUNC(DETECTED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE
                FROM FRAUD_DETECTION_RESULTS
                WHERE DETECTED_AT >= :fromTs AND DETECTED_AT < :toTs
                GROUP BY TRUNC(DETECTED_AT)
                ORDER BY TRUNC(DETECTED_AT)
                """, rangeParams);
        BigDecimal detectionCoverage = safeRate(detectionCount, transactionCount);
        BigDecimal averageDelayMinutes = queryDecimal("""
                SELECT AVG((d.DETECTED_AT - t.CREATED_AT) * 24 * 60)
                FROM FRAUD_DETECTION_RESULTS d
                JOIN TRANSACTIONS t ON t.TX_ID = d.TX_ID
                WHERE d.DETECTED_AT >= :fromTs AND d.DETECTED_AT < :toTs
                """, rangeParams);
        long fraudCount = queryLong("""
                SELECT COUNT(*)
                FROM FRAUD_DETECTION_RESULTS
                WHERE DETECTED_AT >= :fromTs AND DETECTED_AT < :toTs
                  AND IS_FRAUD = 1
                """, rangeParams);
        BigDecimal fraudRate = safeRate(fraudCount, detectionCount);
        Map<String, Long> fraudProbabilityDistribution = loadDistribution("""
                SELECT CASE
                           WHEN FRAUD_PROBABILITY IS NULL THEN 'UNKNOWN'
                           WHEN FRAUD_PROBABILITY < 0.2 THEN '0-0.2'
                           WHEN FRAUD_PROBABILITY < 0.4 THEN '0.2-0.4'
                           WHEN FRAUD_PROBABILITY < 0.6 THEN '0.4-0.6'
                           WHEN FRAUD_PROBABILITY < 0.8 THEN '0.6-0.8'
                           ELSE '0.8-1.0'
                       END AS KEY_NAME,
                       COUNT(*) AS COUNT_VALUE
                FROM FRAUD_DETECTION_RESULTS
                WHERE DETECTED_AT >= :fromTs AND DETECTED_AT < :toTs
                GROUP BY CASE
                           WHEN FRAUD_PROBABILITY IS NULL THEN 'UNKNOWN'
                           WHEN FRAUD_PROBABILITY < 0.2 THEN '0-0.2'
                           WHEN FRAUD_PROBABILITY < 0.4 THEN '0.2-0.4'
                           WHEN FRAUD_PROBABILITY < 0.6 THEN '0.4-0.6'
                           WHEN FRAUD_PROBABILITY < 0.8 THEN '0.6-0.8'
                           ELSE '0.8-1.0'
                       END
                """, rangeParams);
        Map<String, Long> engineDistribution = loadDistribution("""
                SELECT NVL(DETECTED_ENGINE, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM FRAUD_DETECTION_RESULTS
                WHERE DETECTED_AT >= :fromTs AND DETECTED_AT < :toTs
                GROUP BY NVL(DETECTED_ENGINE, 'UNKNOWN')
                """, rangeParams);
        Map<String, Long> thresholdDistribution = loadDistribution("""
                SELECT CASE
                           WHEN THRESHOLD_VALUE IS NULL THEN 'UNKNOWN'
                           WHEN THRESHOLD_VALUE < 0.5 THEN '0-0.5'
                           WHEN THRESHOLD_VALUE < 0.8 THEN '0.5-0.8'
                           WHEN THRESHOLD_VALUE < 1.0 THEN '0.8-1.0'
                           ELSE '1.0+'
                       END AS KEY_NAME,
                       COUNT(*) AS COUNT_VALUE
                FROM FRAUD_DETECTION_RESULTS
                WHERE DETECTED_AT >= :fromTs AND DETECTED_AT < :toTs
                GROUP BY CASE
                           WHEN THRESHOLD_VALUE IS NULL THEN 'UNKNOWN'
                           WHEN THRESHOLD_VALUE < 0.5 THEN '0-0.5'
                           WHEN THRESHOLD_VALUE < 0.8 THEN '0.5-0.8'
                           WHEN THRESHOLD_VALUE < 1.0 THEN '0.8-1.0'
                           ELSE '1.0+'
                       END
                """, rangeParams);
        BigDecimal thresholdExceedRate = queryDecimal("""
                SELECT SUM(CASE WHEN FRAUD_PROBABILITY >= THRESHOLD_VALUE THEN 1 ELSE 0 END)
                       / NULLIF(COUNT(*), 0)
                FROM FRAUD_DETECTION_RESULTS
                WHERE DETECTED_AT >= :fromTs AND DETECTED_AT < :toTs
                  AND THRESHOLD_VALUE IS NOT NULL
                """, rangeParams);

        return new AdminDashboardResponseDTO.DetectionSectionDTO(
                detectionCount,
                detectionTrend,
                detectionCoverage,
                averageDelayMinutes,
                fraudCount,
                fraudRate,
                fraudProbabilityDistribution,
                engineDistribution,
                thresholdDistribution,
                thresholdExceedRate
        );
    }

    /**
     * 신고 섹션 통계를 구성한다. 신고 섹션 응답에 사용될 DTO 객체를 반환한다.
     */
    private AdminDashboardResponseDTO.FraudReportsSectionDTO buildFraudReportsSection(MapSqlParameterSource rangeParams) {
        long totalReports = queryLong("""
                SELECT COUNT(*)
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                """, rangeParams);
        List<AdminDashboardResponseDTO.DateCountDTO> reportTrend = loadAdminDateCounts("""
                SELECT TRUNC(CREATED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY TRUNC(CREATED_AT)
                ORDER BY TRUNC(CREATED_AT)
                """, rangeParams);
        Map<String, Long> statusDistribution = loadDistribution("""
                SELECT NVL(STATUS, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY NVL(STATUS, 'UNKNOWN')
                """, rangeParams);
        List<AdminDashboardResponseDTO.NamedCountDTO> reasonTop = loadNamedCounts("""
                SELECT NVL(REASON, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY NVL(REASON, 'UNKNOWN')
                ORDER BY COUNT(*) DESC
                FETCH NEXT :limit ROWS ONLY
                """, rangeParams);
        Map<String, Long> reasonCodeDistribution = loadDistribution("""
                SELECT NVL(TO_CHAR(REASON_CODE), 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY NVL(TO_CHAR(REASON_CODE), 'UNKNOWN')
                """, rangeParams);
        Map<String, String> reportStatusLabels = loadCodebookLabels("REPORT_STATUS");
        Set<String> reportStatusDuplicates = findDuplicateLabels(reportStatusLabels);
        statusDistribution = mapDistributionLabels(statusDistribution, reportStatusLabels, reportStatusDuplicates);
        Map<String, String> reportReasonLabels = loadCodebookLabels("REPORT_REASON");
        Set<String> reportReasonDuplicates = findDuplicateLabels(reportReasonLabels);
        reasonCodeDistribution = mapDistributionLabels(reasonCodeDistribution, reportReasonLabels, reportReasonDuplicates);
        BigDecimal averageReportCount = queryDecimal("""
                SELECT AVG(REPORT_COUNT)
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                  AND REPORT_COUNT IS NOT NULL
                """, rangeParams);
        long distinctAccountCount = queryLong("""
                SELECT COUNT(DISTINCT REPORTED_ACCOUNT)
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                """, rangeParams);
        BigDecimal duplicateRate = safeRate(totalReports - distinctAccountCount, totalReports);
        List<AdminDashboardResponseDTO.NamedCountDTO> topAccounts = loadNamedCounts("""
                SELECT REPORTED_ACCOUNT AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY REPORTED_ACCOUNT
                ORDER BY COUNT(*) DESC
                FETCH NEXT :limit ROWS ONLY
                """, rangeParams);

        BigDecimal reportedAccountDetectionRate = queryDecimal("""
                SELECT SUM(CASE WHEN d.TX_ID IS NOT NULL THEN 1 ELSE 0 END)
                       / NULLIF(COUNT(*), 0)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                LEFT JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                  AND a.ACCOUNT_NUMBER IN (
                      SELECT DISTINCT REPORTED_ACCOUNT
                      FROM FRAUD_REPORTS
                      WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                  )
                """, rangeParams);
        BigDecimal reportedAccountFraudRate = queryDecimal("""
                SELECT SUM(CASE WHEN d.IS_FRAUD = 1 THEN 1 ELSE 0 END)
                       / NULLIF(COUNT(*), 0)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                  AND a.ACCOUNT_NUMBER IN (
                      SELECT DISTINCT REPORTED_ACCOUNT
                      FROM FRAUD_REPORTS
                      WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                  )
                """, rangeParams);

        return new AdminDashboardResponseDTO.FraudReportsSectionDTO(
                totalReports,
                reportTrend,
                statusDistribution,
                reasonTop,
                reasonCodeDistribution,
                averageReportCount,
                distinctAccountCount,
                duplicateRate,
                topAccounts,
                reportedAccountDetectionRate,
                reportedAccountFraudRate
        );
    }

    /**
     * 블랙리스트 섹션 통계를 구성한다. 블랙리스트 섹션 응답에 사용될 DTO 객체를 반환한다.
     */
    private AdminDashboardResponseDTO.BlacklistSectionDTO buildBlacklistSection(MapSqlParameterSource rangeParams) {
        long totalBlacklist = queryLong("SELECT COUNT(*) FROM BLACKLIST_ACCOUNTS", new MapSqlParameterSource());
        List<AdminDashboardResponseDTO.DateCountDTO> newTrend = loadAdminDateCounts("""
                SELECT TRUNC(BLOCKED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE
                FROM BLACKLIST_ACCOUNTS
                WHERE BLOCKED_AT >= :fromTs AND BLOCKED_AT < :toTs
                GROUP BY TRUNC(BLOCKED_AT)
                ORDER BY TRUNC(BLOCKED_AT)
                """, rangeParams);
        Map<String, Long> reasonDistribution = loadDistribution("""
                SELECT NVL(REASON, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM BLACKLIST_ACCOUNTS
                GROUP BY NVL(REASON, 'UNKNOWN')
                """, new MapSqlParameterSource());
        long distinctAccountCount = queryLong("""
                SELECT COUNT(DISTINCT ACCOUNT_NUM)
                FROM BLACKLIST_ACCOUNTS
                """, new MapSqlParameterSource());
        long duplicateCount = totalBlacklist - distinctAccountCount;
        long relatedTransactionCount = queryLong("""
                SELECT COUNT(*)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                  AND a.ACCOUNT_NUMBER IN (SELECT ACCOUNT_NUM FROM BLACKLIST_ACCOUNTS)
                """, rangeParams);
        long relatedDetectionCount = queryLong("""
                SELECT COUNT(*)
                FROM FRAUD_DETECTION_RESULTS d
                JOIN TRANSACTIONS t ON t.TX_ID = d.TX_ID
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE d.DETECTED_AT >= :fromTs AND d.DETECTED_AT < :toTs
                  AND a.ACCOUNT_NUMBER IN (SELECT ACCOUNT_NUM FROM BLACKLIST_ACCOUNTS)
                """, rangeParams);

        return new AdminDashboardResponseDTO.BlacklistSectionDTO(
                totalBlacklist,
                newTrend,
                reasonDistribution,
                distinctAccountCount,
                duplicateCount,
                relatedTransactionCount,
                relatedDetectionCount
        );
    }

    /**
     * 참고 데이터 섹션 통계를 구성한다. 참고 데이터 섹션 응답에 사용될 DTO 객체를 반환한다.
     */
    private AdminDashboardResponseDTO.ReferenceDataSectionDTO buildReferenceDataSection(MapSqlParameterSource rangeParams) {
        long codebookCount = queryLong("SELECT COUNT(*) FROM STATS_CODEBOOK", new MapSqlParameterSource());
        List<AdminDashboardResponseDTO.DateCountDTO> createdTrend = loadAdminDateCounts("""
                SELECT TRUNC(CREATED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE
                FROM STATS_CODEBOOK
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY TRUNC(CREATED_AT)
                ORDER BY TRUNC(CREATED_AT)
                """, rangeParams);
        List<AdminDashboardResponseDTO.DateCountDTO> updatedTrend = loadAdminDateCounts("""
                SELECT TRUNC(UPDATED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE
                FROM STATS_CODEBOOK
                WHERE UPDATED_AT >= :fromTs AND UPDATED_AT < :toTs
                GROUP BY TRUNC(UPDATED_AT)
                ORDER BY TRUNC(UPDATED_AT)
                """, rangeParams);
        Map<String, Long> codeTypeDistribution = loadDistribution("""
                SELECT NVL(CODE_TYPE, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM STATS_CODEBOOK
                GROUP BY NVL(CODE_TYPE, 'UNKNOWN')
                """, new MapSqlParameterSource());
        long activeCount = queryLong("""
                SELECT COUNT(*)
                FROM STATS_CODEBOOK
                WHERE IS_ACTIVE = 'Y'
                """, new MapSqlParameterSource());
        long inactiveCount = queryLong("""
                SELECT COUNT(*)
                FROM STATS_CODEBOOK
                WHERE IS_ACTIVE = 'N'
                """, new MapSqlParameterSource());
        long metaJsonCount = queryLong("""
                SELECT COUNT(*)
                FROM STATS_CODEBOOK
                WHERE META_JSON IS NOT NULL
                """, new MapSqlParameterSource());
        long descriptionMissingCount = queryLong("""
                SELECT COUNT(*)
                FROM STATS_CODEBOOK
                WHERE DESCRIPTION IS NULL OR TRIM(DESCRIPTION) = ''
                """, new MapSqlParameterSource());
        Map<String, Long> sortOrderDistribution = loadDistribution("""
                SELECT TO_CHAR(NVL(SORT_ORDER, 0)) AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM STATS_CODEBOOK
                GROUP BY NVL(SORT_ORDER, 0)
                ORDER BY NVL(SORT_ORDER, 0)
                """, new MapSqlParameterSource());

        List<AdminDashboardResponseDTO.ConfigEntryDTO> configEntries = jdbcTemplate.query("""
                SELECT CONFIG_KEY, CONFIG_VALUE, DESCRIPTION
                FROM FDS_CONFIG
                ORDER BY CONFIG_KEY
                """, new MapSqlParameterSource(), (rs, rowNum) -> new AdminDashboardResponseDTO.ConfigEntryDTO(
                rs.getString("CONFIG_KEY"),
                rs.getString("CONFIG_VALUE"),
                rs.getString("DESCRIPTION")
        ));

        return new AdminDashboardResponseDTO.ReferenceDataSectionDTO(
                codebookCount,
                createdTrend,
                updatedTrend,
                codeTypeDistribution,
                activeCount,
                inactiveCount,
                metaJsonCount,
                descriptionMissingCount,
                sortOrderDistribution,
                configEntries
        );
    }

    /**
     * 교차 분석 섹션 통계를 구성한다. 교차 분석 섹션 응답에 사용될 DTO 객체를 반환한다.
     */
    private AdminDashboardResponseDTO.CrossEntitySectionDTO buildCrossEntitySection(MapSqlParameterSource rangeParams) {
        List<AdminDashboardResponseDTO.SegmentMetricDTO> segmentMetrics = new ArrayList<>();
        segmentMetrics.addAll(loadSegmentMetrics("""
                SELECT NVL(u.GENDER, 'UNKNOWN') AS SEGMENT,
                       COUNT(*) AS TX_COUNT,
                       SUM(CASE WHEN d.TX_ID IS NOT NULL THEN 1 ELSE 0 END) AS DETECTED_COUNT,
                       SUM(CASE WHEN d.IS_FRAUD = 1 THEN 1 ELSE 0 END) AS FRAUD_COUNT
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN USERS u ON u.ID = a.USER_INNER_ID
                LEFT JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                GROUP BY NVL(u.GENDER, 'UNKNOWN')
                """, rangeParams, "GENDER"));
        segmentMetrics.addAll(loadAgeSegmentMetrics(rangeParams));
        segmentMetrics.addAll(loadSegmentMetrics("""
                SELECT NVL(a.STATUS, 'UNKNOWN') AS SEGMENT,
                       COUNT(*) AS TX_COUNT,
                       SUM(CASE WHEN d.TX_ID IS NOT NULL THEN 1 ELSE 0 END) AS DETECTED_COUNT,
                       SUM(CASE WHEN d.IS_FRAUD = 1 THEN 1 ELSE 0 END) AS FRAUD_COUNT
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                LEFT JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                GROUP BY NVL(a.STATUS, 'UNKNOWN')
                """, rangeParams, "ACCOUNT_STATUS"));
        Map<String, String> accountStatusLabels = loadCodebookLabels("ACCOUNT_STATUS");
        Set<String> accountStatusDuplicates = findDuplicateLabels(accountStatusLabels);
        segmentMetrics = mapSegmentMetrics(segmentMetrics, "ACCOUNT_STATUS", accountStatusLabels, accountStatusDuplicates);

        List<AdminDashboardResponseDTO.AccountRankDTO> accountRanking = jdbcTemplate.query("""
                SELECT a.ACCOUNT_NUMBER AS ACCOUNT_NUMBER,
                       COUNT(*) AS TX_COUNT,
                       SUM(CASE WHEN d.TX_ID IS NOT NULL THEN 1 ELSE 0 END) AS DETECTED_COUNT,
                       SUM(CASE WHEN d.IS_FRAUD = 1 THEN 1 ELSE 0 END) AS FRAUD_COUNT
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                LEFT JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                GROUP BY a.ACCOUNT_NUMBER
                ORDER BY COUNT(*) DESC
                FETCH NEXT :limit ROWS ONLY
                """, withLimit(rangeParams), (rs, rowNum) -> new AdminDashboardResponseDTO.AccountRankDTO(
                rs.getString("ACCOUNT_NUMBER"),
                rs.getLong("TX_COUNT"),
                rs.getLong("DETECTED_COUNT"),
                rs.getLong("FRAUD_COUNT")
        ));

        Map<String, Long> merchantCategoryBreakdown = loadDistribution("""
                SELECT NVL(MERCHANT_CAT, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY NVL(MERCHANT_CAT, 'UNKNOWN')
                """, rangeParams);
        Map<String, Long> locationBreakdown = loadDistribution("""
                SELECT NVL(LOCATION, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY NVL(LOCATION, 'UNKNOWN')
                """, rangeParams);
        Map<String, Long> targetAccountBreakdown = loadDistribution("""
                SELECT NVL(TARGET_ACCOUNT_NUMBER, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY NVL(TARGET_ACCOUNT_NUMBER, 'UNKNOWN')
                """, rangeParams);
        Map<String, Long> transactionTypeBreakdown = loadDistribution("""
                SELECT NVL(TX_TYPE, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY NVL(TX_TYPE, 'UNKNOWN')
                """, rangeParams);
        Map<String, String> transactionTypeLabels = loadCodebookLabels("TRANSACTION_TYPE");
        Set<String> transactionTypeDuplicates = findDuplicateLabels(transactionTypeLabels);
        transactionTypeBreakdown = mapDistributionLabels(
                transactionTypeBreakdown,
                transactionTypeLabels,
                transactionTypeDuplicates
        );

        List<AdminDashboardResponseDTO.FraudBucketDTO> amountBuckets = loadFraudBuckets("""
                SELECT CASE
                           WHEN t.TX_AMOUNT < 100000 THEN '0-100k'
                           WHEN t.TX_AMOUNT < 500000 THEN '100k-500k'
                           WHEN t.TX_AMOUNT < 1000000 THEN '500k-1m'
                           ELSE '1m+'
                       END AS BUCKET,
                       COUNT(*) AS TX_COUNT,
                       SUM(CASE WHEN d.IS_FRAUD = 1 THEN 1 ELSE 0 END) AS FRAUD_COUNT,
                       AVG(d.FRAUD_PROBABILITY) AS AVG_PROB
                FROM TRANSACTIONS t
                LEFT JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                GROUP BY CASE
                           WHEN t.TX_AMOUNT < 100000 THEN '0-100k'
                           WHEN t.TX_AMOUNT < 500000 THEN '100k-500k'
                           WHEN t.TX_AMOUNT < 1000000 THEN '500k-1m'
                           ELSE '1m+'
                       END
                """, rangeParams);
        List<AdminDashboardResponseDTO.FraudBucketDTO> hourBuckets = loadFraudBuckets("""
                SELECT TO_CHAR(EXTRACT(HOUR FROM t.CREATED_AT)) AS BUCKET,
                       COUNT(*) AS TX_COUNT,
                       SUM(CASE WHEN d.IS_FRAUD = 1 THEN 1 ELSE 0 END) AS FRAUD_COUNT,
                       AVG(d.FRAUD_PROBABILITY) AS AVG_PROB
                FROM TRANSACTIONS t
                LEFT JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                GROUP BY EXTRACT(HOUR FROM t.CREATED_AT)
                ORDER BY EXTRACT(HOUR FROM t.CREATED_AT)
                """, rangeParams);
        List<AdminDashboardResponseDTO.FraudBucketDTO> typeBuckets = loadFraudBuckets("""
                SELECT NVL(t.TX_TYPE, 'UNKNOWN') AS BUCKET,
                       COUNT(*) AS TX_COUNT,
                       SUM(CASE WHEN d.IS_FRAUD = 1 THEN 1 ELSE 0 END) AS FRAUD_COUNT,
                       AVG(d.FRAUD_PROBABILITY) AS AVG_PROB
                FROM TRANSACTIONS t
                LEFT JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                GROUP BY NVL(t.TX_TYPE, 'UNKNOWN')
                """, rangeParams);
        typeBuckets = mapFraudBuckets(typeBuckets, transactionTypeLabels, transactionTypeDuplicates);
        List<AdminDashboardResponseDTO.EngineComparisonDTO> engineComparisons = jdbcTemplate.query("""
                SELECT NVL(DETECTED_ENGINE, 'UNKNOWN') AS ENGINE,
                       AVG(FRAUD_PROBABILITY) AS AVG_PROB,
                       SUM(CASE WHEN IS_FRAUD = 1 THEN 1 ELSE 0 END) AS FRAUD_COUNT,
                       COUNT(*) AS TOTAL_COUNT
                FROM FRAUD_DETECTION_RESULTS
                WHERE DETECTED_AT >= :fromTs AND DETECTED_AT < :toTs
                GROUP BY NVL(DETECTED_ENGINE, 'UNKNOWN')
                """, rangeParams, (rs, rowNum) -> {
            long fraudCount = rs.getLong("FRAUD_COUNT");
            long totalCount = rs.getLong("TOTAL_COUNT");
            return new AdminDashboardResponseDTO.EngineComparisonDTO(
                    rs.getString("ENGINE"),
                    rs.getDouble("AVG_PROB"),
                    safeRate(fraudCount, totalCount),
                    totalCount
            );
        });

        BigDecimal blacklistDetectionRate = queryDecimal("""
                SELECT SUM(CASE WHEN d.TX_ID IS NOT NULL THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                LEFT JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                  AND a.ACCOUNT_NUMBER IN (SELECT ACCOUNT_NUM FROM BLACKLIST_ACCOUNTS)
                """, rangeParams);

        return new AdminDashboardResponseDTO.CrossEntitySectionDTO(
                segmentMetrics,
                accountRanking,
                merchantCategoryBreakdown,
                locationBreakdown,
                targetAccountBreakdown,
                transactionTypeBreakdown,
                amountBuckets,
                hourBuckets,
                typeBuckets,
                engineComparisons,
                blacklistDetectionRate
        );
    }

    /**
     * 사용자 생년을 연령대 버킷으로 묶어 분포를 계산한다. 연령대별 사용자 수 분포 응답에 사용될 Map 데이터를 반환한다.
     */
    private Map<String, Long> computeAgeDistribution() {
        List<String> birthValues = jdbcTemplate.query(
                "SELECT BIRTH FROM USERS WHERE BIRTH IS NOT NULL",
                (rs, rowNum) -> rs.getString("BIRTH")
        );
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (String birth : birthValues) {
            String group = toAgeGroup(birth);
            distribution.merge(group, 1L, Long::sum);
        }
        return distribution;
    }

    /**
     * 성별/연령대별 계좌 평균 잔액을 계산한다. 성별/연령대별 평균 잔액 목록 배열을 반환한다.
     */
    private List<AdminDashboardResponseDTO.SegmentAverageDTO> computeAverageBalanceByGenderAge() {
        List<UserBalanceRow> rows = jdbcTemplate.query("""
                SELECT u.GENDER, u.BIRTH, a.BALANCE
                FROM ACCOUNTS a
                JOIN USERS u ON u.ID = a.USER_INNER_ID
                """, (rs, rowNum) -> new UserBalanceRow(
                rs.getString("GENDER"),
                rs.getString("BIRTH"),
                rs.getBigDecimal("BALANCE")
        ));
        Map<String, List<BigDecimal>> grouped = new LinkedHashMap<>();
        for (UserBalanceRow row : rows) {
            String gender = normalizeText(row.gender());
            if (gender == null) {
                gender = "UNKNOWN";
            }
            String ageGroup = toAgeGroup(row.birth());
            String key = gender + "|" + ageGroup;
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row.balance());
        }

        List<AdminDashboardResponseDTO.SegmentAverageDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<BigDecimal>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("\\|", -1);
            String gender = parts.length > 0 ? parts[0] : "UNKNOWN";
            String ageGroup = parts.length > 1 ? parts[1] : "UNKNOWN";
            BigDecimal average = average(entry.getValue());
            result.add(new AdminDashboardResponseDTO.SegmentAverageDTO(gender, ageGroup, average));
        }
        return result;
    }

    private Map<String, Long> computeCountDistribution(String sql) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            long count = toLong(row.get(COUNT_VALUE));
            distribution.merge(String.valueOf(count), 1L, Long::sum);
        }
        return distribution;
    }

    private List<AdminDashboardResponseDTO.SegmentMetricDTO> loadSegmentMetrics(
            String sql,
            MapSqlParameterSource params,
            String segmentType
    ) {
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new AdminDashboardResponseDTO.SegmentMetricDTO(
                segmentType,
                rs.getString("SEGMENT"),
                rs.getLong("TX_COUNT"),
                rs.getLong("DETECTED_COUNT"),
                rs.getLong("FRAUD_COUNT")
        ));
    }

    private List<AdminDashboardResponseDTO.SegmentMetricDTO> loadAgeSegmentMetrics(MapSqlParameterSource params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT u.BIRTH AS BIRTH,
                       COUNT(*) AS TX_COUNT,
                       SUM(CASE WHEN d.TX_ID IS NOT NULL THEN 1 ELSE 0 END) AS DETECTED_COUNT,
                       SUM(CASE WHEN d.IS_FRAUD = 1 THEN 1 ELSE 0 END) AS FRAUD_COUNT
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN USERS u ON u.ID = a.USER_INNER_ID
                LEFT JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                GROUP BY u.BIRTH
                """, params);
        Map<String, long[]> aggregated = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String birth = Objects.toString(row.get("BIRTH"), null);
            String group = toAgeGroup(birth);
            long[] metrics = aggregated.computeIfAbsent(group, ignored -> new long[3]);
            metrics[0] += toLong(row.get("TX_COUNT"));
            metrics[1] += toLong(row.get("DETECTED_COUNT"));
            metrics[2] += toLong(row.get("FRAUD_COUNT"));
        }
        List<AdminDashboardResponseDTO.SegmentMetricDTO> result = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : aggregated.entrySet()) {
            long[] metrics = entry.getValue();
            result.add(new AdminDashboardResponseDTO.SegmentMetricDTO(
                    "AGE_GROUP",
                    entry.getKey(),
                    metrics[0],
                    metrics[1],
                    metrics[2]
            ));
        }
        return result;
    }

    private AdminDashboardResponseDTO.FieldStatsDTO buildFieldStats(
            String columnName,
            MapSqlParameterSource params
    ) {
        long total = queryLong("""
                SELECT COUNT(*)
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                """, params);
        long missing = queryLong("""
                SELECT COUNT(*)
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                  AND (%s IS NULL OR TRIM(%s) = '')
                """.formatted(columnName, columnName), params);
        List<AdminDashboardResponseDTO.NamedCountDTO> topValues = loadNamedCounts("""
                SELECT %s AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                  AND %s IS NOT NULL AND TRIM(%s) <> ''
                GROUP BY %s
                ORDER BY COUNT(*) DESC
                FETCH NEXT :limit ROWS ONLY
                """.formatted(columnName, columnName, columnName, columnName), params);
        return new AdminDashboardResponseDTO.FieldStatsDTO(
                total,
                missing,
                safeRate(missing, total),
                topValues
        );
    }

    private AdminDashboardResponseDTO.NumericSummaryDTO buildNumericSummary(String columnName) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        BigDecimal min = queryDecimal("""
                SELECT MIN(%s)
                FROM TRANSACTION_FEATURES
                """.formatted(columnName), params);
        BigDecimal max = queryDecimal("""
                SELECT MAX(%s)
                FROM TRANSACTION_FEATURES
                """.formatted(columnName), params);
        BigDecimal avg = queryDecimal("""
                SELECT AVG(%s)
                FROM TRANSACTION_FEATURES
                """.formatted(columnName), params);
        return new AdminDashboardResponseDTO.NumericSummaryDTO(columnName, min, max, avg);
    }

    private List<AdminDashboardResponseDTO.FraudBucketDTO> loadFraudBuckets(
            String sql,
            MapSqlParameterSource params
    ) {
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            long txCount = rs.getLong("TX_COUNT");
            long fraudCount = rs.getLong("FRAUD_COUNT");
            Double avgProb = rs.getObject("AVG_PROB") == null ? null : rs.getDouble("AVG_PROB");
            return new AdminDashboardResponseDTO.FraudBucketDTO(
                    rs.getString("BUCKET"),
                    txCount,
                    fraudCount,
                    safeRate(fraudCount, txCount),
                    avgProb
            );
        });
    }

    private List<AdminDashboardResponseDTO.DateCountDTO> loadAdminDateCounts(
            String sql,
            MapSqlParameterSource params
    ) {
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new AdminDashboardResponseDTO.DateCountDTO(
                toLocalDate(rs.getTimestamp(KEY_DATE)),
                rs.getLong(COUNT_VALUE)
        ));
    }

    private List<AdminDashboardResponseDTO.NamedCountDTO> loadNamedCounts(
            String sql,
            MapSqlParameterSource params
    ) {
        MapSqlParameterSource withLimit = withLimit(params);
        return jdbcTemplate.query(sql, withLimit, (rs, rowNum) -> new AdminDashboardResponseDTO.NamedCountDTO(
                rs.getString(KEY_NAME),
                rs.getLong(COUNT_VALUE)
        ));
    }

    private List<AdminDashboardResponseDTO.NamedAmountDTO> loadNamedAmounts(
            String sql,
            MapSqlParameterSource params
    ) {
        MapSqlParameterSource withLimit = withLimit(params);
        return jdbcTemplate.query(sql, withLimit, (rs, rowNum) -> new AdminDashboardResponseDTO.NamedAmountDTO(
                rs.getString(KEY_NAME),
                rs.getBigDecimal(AMOUNT_VALUE)
        ));
    }

    private MapSqlParameterSource withLimit(MapSqlParameterSource params) {
        MapSqlParameterSource next = new MapSqlParameterSource();
        if (params != null) {
            params.getValues().forEach(next::addValue);
        }
        next.addValue("limit", TOP_LIMIT);
        return next;
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal value : values) {
            if (value == null) {
                continue;
            }
            sum = sum.add(value);
            count++;
        }
        if (count == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
    }

    private List<AdminDashboardResponseDTO.SegmentMetricDTO> mapSegmentMetrics(
            List<AdminDashboardResponseDTO.SegmentMetricDTO> metrics,
            String segmentType,
            Map<String, String> labels,
            Set<String> duplicateLabels
    ) {
        if (metrics == null || metrics.isEmpty() || labels == null || labels.isEmpty()) {
            return metrics;
        }
        List<AdminDashboardResponseDTO.SegmentMetricDTO> mapped = new ArrayList<>(metrics.size());
        for (AdminDashboardResponseDTO.SegmentMetricDTO metric : metrics) {
            if (!segmentType.equals(metric.segmentType())) {
                mapped.add(metric);
                continue;
            }
            String mappedValue = resolveCodeLabel(metric.segmentValue(), labels, duplicateLabels);
            mapped.add(new AdminDashboardResponseDTO.SegmentMetricDTO(
                    metric.segmentType(),
                    mappedValue,
                    metric.transactionCount(),
                    metric.detectedCount(),
                    metric.fraudCount()
            ));
        }
        return mapped;
    }

    private List<AdminDashboardResponseDTO.FraudBucketDTO> mapFraudBuckets(
            List<AdminDashboardResponseDTO.FraudBucketDTO> buckets,
            Map<String, String> labels,
            Set<String> duplicateLabels
    ) {
        if (buckets == null || buckets.isEmpty() || labels == null || labels.isEmpty()) {
            return buckets;
        }
        List<AdminDashboardResponseDTO.FraudBucketDTO> mapped = new ArrayList<>(buckets.size());
        for (AdminDashboardResponseDTO.FraudBucketDTO bucket : buckets) {
            String mappedLabel = resolveCodeLabel(bucket.bucket(), labels, duplicateLabels);
            mapped.add(new AdminDashboardResponseDTO.FraudBucketDTO(
                    mappedLabel,
                    bucket.transactionCount(),
                    bucket.fraudCount(),
                    bucket.fraudRate(),
                    bucket.averageFraudProbability()
            ));
        }
        return mapped;
    }

    private String toAgeGroup(String birth) {
        if (birth == null || birth.isBlank()) {
            return "UNKNOWN";
        }
        String trimmed = birth.trim();
        if (trimmed.length() < 4) {
            return "UNKNOWN";
        }
        String yearText = trimmed.substring(0, 4);
        int year;
        try {
            year = Integer.parseInt(yearText);
        } catch (NumberFormatException ex) {
            return "UNKNOWN";
        }
        int currentYear = LocalDate.now(DEFAULT_ZONE).getYear();
        int age = currentYear - year;
        if (age < 0 || age > 120) {
            return "UNKNOWN";
        }
        int bucket = (age / 10) * 10;
        return bucket + "s";
    }

    private record UserBalanceRow(String gender, String birth, BigDecimal balance) { }
}
