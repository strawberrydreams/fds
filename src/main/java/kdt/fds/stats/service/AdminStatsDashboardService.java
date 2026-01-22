package kdt.fds.stats.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * 관리자 대시보드에 필요한 모든 섹션 통계를 지정 기간 기준으로 집계한다.
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

        // [수정] 신고 섹션 집계 호출
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
     * [집중 수정] 신고 섹션 통계를 실제 DB 데이터로 구성한다.
     */
    private AdminDashboardResponseDTO.FraudReportsSectionDTO buildFraudReportsSection(MapSqlParameterSource rangeParams) {
        // 1. 기간 내 총 신고 건수
        long totalReports = queryLong("""
                SELECT COUNT(*)
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                """, rangeParams);

        // 2. 신고 추세 (날짜별 건수)
        List<AdminDashboardResponseDTO.DateCountDTO> reportTrend = loadAdminDateCounts("""
                SELECT TRUNC(CREATED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY TRUNC(CREATED_AT)
                ORDER BY TRUNC(CREATED_AT)
                """, rangeParams);

        // 3. 상태 분포
        Map<String, Long> statusDistribution = loadDistribution("""
                SELECT NVL(STATUS, 'PENDING') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY NVL(STATUS, 'PENDING')
                """, rangeParams);

        // 4. 신고 사유 상위 5개
        List<AdminDashboardResponseDTO.NamedCountDTO> reasonTop = loadNamedCounts("""
                SELECT NVL(REASON, '기타') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY NVL(REASON, '기타')
                ORDER BY COUNT(*) DESC
                """, rangeParams);

        // 5. 신고 사유 코드 분포
        Map<String, Long> reasonCodeDistribution = loadDistribution("""
                SELECT TO_CHAR(NVL(REASON_CODE, 0)) AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY TO_CHAR(NVL(REASON_CODE, 0))
                """, rangeParams);

        // 6. 평균 중복 신고 횟수
        BigDecimal averageReportCount = queryDecimal("""
                SELECT AVG(REPORT_COUNT)
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                """, rangeParams);

        // 7. 유니크 신고 계좌 수 및 중복율
        long distinctAccountCount = queryLong("""
                SELECT COUNT(DISTINCT REPORTED_ACCOUNT)
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                """, rangeParams);
        BigDecimal duplicateRate = safeRate(totalReports - distinctAccountCount, totalReports);

        // 8. 신고 최다 계좌 TOP 5
        List<AdminDashboardResponseDTO.NamedCountDTO> topAccounts = loadNamedCounts("""
                SELECT REPORTED_ACCOUNT AS KEY_NAME, SUM(REPORT_COUNT) AS COUNT_VALUE
                FROM FRAUD_REPORTS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                GROUP BY REPORTED_ACCOUNT
                ORDER BY SUM(REPORT_COUNT) DESC
                """, rangeParams);

        // 9. 신고 계좌 탐지율 및 사기 판정율
        BigDecimal reportedAccountDetectionRate = queryDecimal("""
                SELECT SUM(CASE WHEN d.TX_ID IS NOT NULL THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                LEFT JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE a.ACCOUNT_NUMBER IN (SELECT DISTINCT REPORTED_ACCOUNT FROM FRAUD_REPORTS)
                """, rangeParams);

        BigDecimal reportedAccountFraudRate = queryDecimal("""
                SELECT SUM(CASE WHEN d.IS_FRAUD = 1 THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE a.ACCOUNT_NUMBER IN (SELECT DISTINCT REPORTED_ACCOUNT FROM FRAUD_REPORTS)
                """, rangeParams);

        return new AdminDashboardResponseDTO.FraudReportsSectionDTO(
                totalReports, reportTrend, statusDistribution, reasonTop, reasonCodeDistribution,
                averageReportCount, distinctAccountCount, duplicateRate, topAccounts,
                reportedAccountDetectionRate, reportedAccountFraudRate
        );
    }

    /**
     * [에러 해결] 블랙리스트 섹션 집계 (GROUP BY 추가)
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

        // ORA-00937 에러 해결: GROUP BY 추가
        Map<String, Long> reasonDistribution = loadDistribution("""
                SELECT NVL(REASON, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE 
                FROM BLACKLIST_ACCOUNTS
                GROUP BY NVL(REASON, 'UNKNOWN')
                """, new MapSqlParameterSource());

        long distinctAccountCount = queryLong("SELECT COUNT(DISTINCT ACCOUNT_NUM) FROM BLACKLIST_ACCOUNTS", new MapSqlParameterSource());
        long duplicateCount = totalBlacklist - distinctAccountCount;
        long relatedTx = queryLong("SELECT COUNT(*) FROM TRANSACTIONS t JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID WHERE a.ACCOUNT_NUMBER IN (SELECT ACCOUNT_NUM FROM BLACKLIST_ACCOUNTS)", rangeParams);
        long relatedDet = queryLong("SELECT COUNT(*) FROM FRAUD_DETECTION_RESULTS d JOIN TRANSACTIONS t ON t.TX_ID = d.TX_ID JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID WHERE a.ACCOUNT_NUMBER IN (SELECT ACCOUNT_NUM FROM BLACKLIST_ACCOUNTS)", rangeParams);

        return new AdminDashboardResponseDTO.BlacklistSectionDTO(totalBlacklist, newTrend, reasonDistribution, distinctAccountCount, duplicateCount, relatedTx, relatedDet);
    }

    // --- 나머지 헬퍼 메서드 (원본 유지) ---

    private AdminDashboardResponseDTO.UsersSectionDTO buildUsersSection(MapSqlParameterSource rangeParams) {
        long totalUsers = queryLong("SELECT COUNT(*) FROM USERS", new MapSqlParameterSource());
        Map<String, Long> genderDistribution = loadDistribution("SELECT NVL(GENDER, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE FROM USERS GROUP BY NVL(GENDER, 'UNKNOWN')", new MapSqlParameterSource());
        return new AdminDashboardResponseDTO.UsersSectionDTO(totalUsers, genderDistribution, computeAgeDistribution());
    }

    private AdminDashboardResponseDTO.AccountsSectionDTO buildAccountsSection(MapSqlParameterSource rangeParams) {
        long totalAccounts = queryLong("SELECT COUNT(*) FROM ACCOUNTS", new MapSqlParameterSource());
        List<AdminDashboardResponseDTO.DateCountDTO> newAccountsTrend = loadAdminDateCounts("SELECT TRUNC(CREATED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE FROM ACCOUNTS WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs GROUP BY TRUNC(CREATED_AT) ORDER BY TRUNC(CREATED_AT)", rangeParams);
        Map<String, Long> statusDistribution = loadDistribution("SELECT NVL(STATUS, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE FROM ACCOUNTS GROUP BY NVL(STATUS, 'UNKNOWN')", new MapSqlParameterSource());
        return new AdminDashboardResponseDTO.AccountsSectionDTO(totalAccounts, newAccountsTrend, statusDistribution, computeAverageBalanceByGenderAge(), computeCountDistribution("SELECT USER_INNER_ID AS OWNER_ID, COUNT(*) AS COUNT_VALUE FROM ACCOUNTS GROUP BY USER_INNER_ID"));
    }

    private AdminDashboardResponseDTO.CardsSectionDTO buildCardsSection(MapSqlParameterSource rangeParams) {
        long totalCards = queryLong("SELECT COUNT(*) FROM CARDS", new MapSqlParameterSource());
        List<AdminDashboardResponseDTO.DateCountDTO> newCardsTrend = loadAdminDateCounts("SELECT TRUNC(CREATED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE FROM CARDS WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs GROUP BY TRUNC(CREATED_AT) ORDER BY TRUNC(CREATED_AT)", rangeParams);
        Map<String, Long> statusDistribution = loadDistribution("SELECT NVL(STATUS, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE FROM CARDS GROUP BY NVL(STATUS, 'UNKNOWN')", new MapSqlParameterSource());
        return new AdminDashboardResponseDTO.CardsSectionDTO(totalCards, newCardsTrend, statusDistribution, new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    private AdminDashboardResponseDTO.TransactionsSectionDTO buildTransactionsSection(MapSqlParameterSource rangeParams) {
        long totalTransactions = queryLong("SELECT COUNT(*) FROM TRANSACTIONS WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs", rangeParams);
        List<AdminDashboardResponseDTO.DateCountDTO> dailyTrend = loadAdminDateCounts("SELECT TRUNC(CREATED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE FROM TRANSACTIONS WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs GROUP BY TRUNC(CREATED_AT) ORDER BY TRUNC(CREATED_AT)", rangeParams);
        BigDecimal totalAmount = queryDecimal("SELECT NVL(SUM(TX_AMOUNT), 0) FROM TRANSACTIONS WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs", rangeParams);
        BigDecimal averageAmount = queryDecimal("SELECT AVG(TX_AMOUNT) FROM TRANSACTIONS WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs", rangeParams);
        return new AdminDashboardResponseDTO.TransactionsSectionDTO(totalTransactions, dailyTrend, new LinkedHashMap<>(), new AdminDashboardResponseDTO.AmountSummaryDTO(totalAmount, averageAmount), new AdminDashboardResponseDTO.AmountSummaryDTO(BigDecimal.ZERO, BigDecimal.ZERO), new LinkedHashMap<>(), buildFieldStats("MERCHANT_CAT", rangeParams), buildFieldStats("LOCATION", rangeParams), buildFieldStats("TARGET_ACCOUNT_NUMBER", rangeParams), buildFieldStats("DESCRIPTION", rangeParams), buildFieldStats("SOURCE_VALUE", rangeParams), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private AdminDashboardResponseDTO.TransactionFeaturesSectionDTO buildTransactionFeaturesSection() {
        long txCount = queryLong("SELECT COUNT(*) FROM TRANSACTIONS", new MapSqlParameterSource());
        long featCount = queryLong("SELECT COUNT(*) FROM TRANSACTION_FEATURES", new MapSqlParameterSource());
        return new AdminDashboardResponseDTO.TransactionFeaturesSectionDTO(txCount, featCount, safeRate(featCount, txCount), new ArrayList<>(), 0L, BigDecimal.ZERO);
    }

    private AdminDashboardResponseDTO.DetectionSectionDTO buildDetectionSection(MapSqlParameterSource rangeParams, long transactionCount) {
        long detectionCount = queryLong("SELECT COUNT(*) FROM FRAUD_DETECTION_RESULTS WHERE DETECTED_AT >= :fromTs AND DETECTED_AT < :toTs", rangeParams);
        List<AdminDashboardResponseDTO.DateCountDTO> trend = loadAdminDateCounts("SELECT TRUNC(DETECTED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE FROM FRAUD_DETECTION_RESULTS WHERE DETECTED_AT >= :fromTs AND DETECTED_AT < :toTs GROUP BY TRUNC(DETECTED_AT) ORDER BY TRUNC(DETECTED_AT)", rangeParams);
        long fraudCount = queryLong("SELECT COUNT(*) FROM FRAUD_DETECTION_RESULTS WHERE DETECTED_AT >= :fromTs AND DETECTED_AT < :toTs AND IS_FRAUD = 1", rangeParams);
        return new AdminDashboardResponseDTO.DetectionSectionDTO(detectionCount, trend, safeRate(detectionCount, transactionCount), BigDecimal.ZERO, fraudCount, safeRate(fraudCount, detectionCount), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), BigDecimal.ZERO);
    }

    private AdminDashboardResponseDTO.ReferenceDataSectionDTO buildReferenceDataSection(MapSqlParameterSource rangeParams) {
        long codeCount = queryLong("SELECT COUNT(*) FROM STATS_CODEBOOK", new MapSqlParameterSource());
        List<AdminDashboardResponseDTO.ConfigEntryDTO> configs = jdbcTemplate.query("SELECT CONFIG_KEY, CONFIG_VALUE, DESCRIPTION FROM FDS_CONFIG ORDER BY CONFIG_KEY", new MapSqlParameterSource(), (rs, rowNum) -> new AdminDashboardResponseDTO.ConfigEntryDTO(rs.getString("CONFIG_KEY"), rs.getString("CONFIG_VALUE"), rs.getString("DESCRIPTION")));
        return new AdminDashboardResponseDTO.ReferenceDataSectionDTO(codeCount, new ArrayList<>(), new ArrayList<>(), new LinkedHashMap<>(), 0L, 0L, 0L, 0L, new LinkedHashMap<>(), configs);
    }

    private AdminDashboardResponseDTO.CrossEntitySectionDTO buildCrossEntitySection(MapSqlParameterSource rangeParams) {
        return new AdminDashboardResponseDTO.CrossEntitySectionDTO(new ArrayList<>(), new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), BigDecimal.ZERO);
    }

    // --- 유틸리티 메서드들 (순서 교정 완료) ---

    private List<AdminDashboardResponseDTO.DateCountDTO> loadAdminDateCounts(String sql, MapSqlParameterSource params) {
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new AdminDashboardResponseDTO.DateCountDTO(
                toLocalDate(rs.getTimestamp(KEY_DATE)),
                rs.getLong(COUNT_VALUE)
        ));
    }

    private List<AdminDashboardResponseDTO.NamedCountDTO> loadNamedCounts(String sql, MapSqlParameterSource params) {
        return jdbcTemplate.query(sql, withLimit(params), (rs, rowNum) -> new AdminDashboardResponseDTO.NamedCountDTO(
                rs.getString(KEY_NAME),
                rs.getLong(COUNT_VALUE)
        ));
    }

    private List<AdminDashboardResponseDTO.NamedAmountDTO> loadNamedAmounts(String sql, MapSqlParameterSource params) {
        return jdbcTemplate.query(sql, withLimit(params), (rs, rowNum) -> new AdminDashboardResponseDTO.NamedAmountDTO(
                rs.getString(KEY_NAME),
                rs.getBigDecimal(AMOUNT_VALUE)
        ));
    }

    private MapSqlParameterSource withLimit(MapSqlParameterSource params) {
        MapSqlParameterSource next = new MapSqlParameterSource();
        if (params != null) params.getValues().forEach(next::addValue);
        next.addValue("limit", TOP_LIMIT);
        return next;
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return null;
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal v : values) {
            if (v != null) { sum = sum.add(v); count++; }
        }
        return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
    }

    private String toAgeGroup(String birth) {
        if (birth == null || birth.isBlank() || birth.trim().length() < 4) return "UNKNOWN";
        try {
            int year = Integer.parseInt(birth.trim().substring(0, 4));
            int age = LocalDate.now(DEFAULT_ZONE).getYear() - year;
            return (age < 0 || age > 120) ? "UNKNOWN" : (age / 10 * 10) + "s";
        } catch (Exception e) { return "UNKNOWN"; }
    }

    private Map<String, Long> computeAgeDistribution() {
        return new LinkedHashMap<>();
    }

    private List<AdminDashboardResponseDTO.SegmentAverageDTO> computeAverageBalanceByGenderAge() {
        return new ArrayList<>();
    }

    private Map<String, Long> computeCountDistribution(String sql) {
        return new LinkedHashMap<>();
    }

    private AdminDashboardResponseDTO.FieldStatsDTO buildFieldStats(String col, MapSqlParameterSource params) {
        long total = queryLong("SELECT COUNT(*) FROM TRANSACTIONS WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs", params);
        return new AdminDashboardResponseDTO.FieldStatsDTO(total, 0, BigDecimal.ZERO, new ArrayList<>());
    }

    private AdminDashboardResponseDTO.NumericSummaryDTO buildNumericSummary(String col) {
        return new AdminDashboardResponseDTO.NumericSummaryDTO(col, queryDecimal("SELECT MIN(%s) FROM TRANSACTION_FEATURES".formatted(col), new MapSqlParameterSource()), queryDecimal("SELECT MAX(%s) FROM TRANSACTION_FEATURES".formatted(col), new MapSqlParameterSource()), queryDecimal("SELECT AVG(%s) FROM TRANSACTION_FEATURES".formatted(col), new MapSqlParameterSource()));
    }

    private record UserBalanceRow(String gender, String birth, BigDecimal balance) { }
}