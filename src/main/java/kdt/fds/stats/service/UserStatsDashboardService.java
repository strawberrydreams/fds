package kdt.fds.stats.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kdt.fds.stats.dto.response.UserDashboardResponseDTO;
import kdt.fds.stats.dto.response.UserSummaryResponseDTO;
import kdt.fds.stats.vo.StatsDateRange;
import kdt.fds.stats.vo.StatsRangeType;
import kdt.fds.project.entity.User;
import kdt.fds.project.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 사용자 대시보드에 필요한 집계를 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class UserStatsDashboardService extends StatsDashboardSupport {
    private final UserRepository userRepository;

    public UserStatsDashboardService(
            NamedParameterJdbcTemplate jdbcTemplate,
            UserRepository userRepository
    ) {
        super(jdbcTemplate);
        this.userRepository = userRepository;
    }

    /**
     * 사용자 거래/탐지 요약 지표를 지정 기간 기준으로 집계한다. 사용자 요약 통계 응답에 사용될 DTO 객체를 반환한다.
     */
    public UserSummaryResponseDTO getUserSummary(Long userId, StatsRangeType rangeType) {
        StatsDateRange range = resolveRange(rangeType);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("fromTs", range.fromTimestamp())
                .addValue("toTs", range.toExclusiveTimestamp());

        long transactionCount = queryLong("""
                SELECT COUNT(*)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                """, params);
        BigDecimal totalAmount = queryDecimal("""
                SELECT NVL(SUM(t.TX_AMOUNT), 0)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                """, params);
        BigDecimal averageAmount = queryDecimal("""
                SELECT AVG(t.TX_AMOUNT)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                """, params);

        long detectedCount = queryLong("""
                SELECT COUNT(*)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                """, params);
        long fraudCount = queryLong("""
                SELECT COUNT(*)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                  AND d.IS_FRAUD = 1
                """, params);

        Double averageFraudProbability = queryAverageFraudProbability(params);
        Double medianFraudProbability = computeMedianProbability(params);

        LocalDateTime latestTransactionAt = queryTimestamp("""
                SELECT MAX(t.CREATED_AT)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                """, params);
        LocalDateTime latestDetectionAt = queryTimestamp("""
                SELECT MAX(d.DETECTED_AT)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                """, params);

        return new UserSummaryResponseDTO(
                rangeType == null ? StatsRangeType.LAST_7_DAYS.name() : rangeType.name(),
                transactionCount,
                totalAmount,
                averageAmount,
                detectedCount,
                safeRate(detectedCount, transactionCount),
                fraudCount,
                safeRate(fraudCount, detectedCount),
                averageFraudProbability,
                medianFraudProbability,
                latestTransactionAt,
                latestDetectionAt
        );
    }

    /**
     * 사용자 대시보드에 필요한 프로필/계좌/카드/거래/탐지 정보를 조회해 구성한다. 사용자 대시보드 응답에 사용될 DTO 객체를 반환한다.
     */
    public UserDashboardResponseDTO getUserDashboard(Long userId, StatsRangeType rangeType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        StatsDateRange range = resolveRange(rangeType);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("fromTs", range.fromTimestamp())
                .addValue("toTs", range.toExclusiveTimestamp());

        Map<String, String> accountStatusLabels = loadCodebookLabels("ACCOUNT_STATUS");
        Set<String> accountStatusDuplicates = findDuplicateLabels(accountStatusLabels);
        Map<String, String> cardStatusLabels = loadCodebookLabels("CARD_STATUS");
        Set<String> cardStatusDuplicates = findDuplicateLabels(cardStatusLabels);
        Map<String, String> cardTypeLabels = loadCodebookLabels("CARD_TYPE");
        Set<String> cardTypeDuplicates = findDuplicateLabels(cardTypeLabels);
        Map<String, String> transactionTypeLabels = loadCodebookLabels("TRANSACTION_TYPE");
        Set<String> transactionTypeDuplicates = findDuplicateLabels(transactionTypeLabels);

        UserDashboardResponseDTO.UserProfileDTO profile = new UserDashboardResponseDTO.UserProfileDTO(
                user.getId(),
                user.getUserId(),
                user.getName()
        );

        List<UserDashboardResponseDTO.AccountDTO> accounts = jdbcTemplate.query("""
                SELECT ACCOUNT_ID, ACCOUNT_NUMBER, STATUS, BALANCE, CREATED_AT
                FROM ACCOUNTS
                WHERE USER_INNER_ID = :userId
                ORDER BY CREATED_AT DESC
                """, params, (rs, rowNum) -> new UserDashboardResponseDTO.AccountDTO(
                rs.getLong("ACCOUNT_ID"),
                rs.getString("ACCOUNT_NUMBER"),
                resolveCodeLabel(rs.getString("STATUS"), accountStatusLabels, accountStatusDuplicates),
                rs.getBigDecimal("BALANCE"),
                toLocalDateTime(rs.getTimestamp("CREATED_AT"))
        ));

        long cardCount = queryLong("""
                SELECT COUNT(*)
                FROM CARDS
                WHERE USER_INNER_ID = :userId
                """, params);
        BigDecimal averageCardsPerUser = queryDecimal("""
                SELECT COUNT(*) / NULLIF(COUNT(DISTINCT USER_INNER_ID), 0)
                FROM CARDS
                """, new MapSqlParameterSource());
        Map<String, Long> cardStatusCounts = loadDistribution("""
                SELECT NVL(STATUS, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM CARDS
                WHERE USER_INNER_ID = :userId
                GROUP BY NVL(STATUS, 'UNKNOWN')
                """, params);
        Map<String, Long> cardTypeCounts = loadDistribution("""
                SELECT NVL(CARD_TYPE, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM CARDS
                WHERE USER_INNER_ID = :userId
                GROUP BY NVL(CARD_TYPE, 'UNKNOWN')
                """, params);
        Map<String, Long> cardIssuerCounts = loadDistribution("""
                SELECT NVL(ISSUER, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM CARDS
                WHERE USER_INNER_ID = :userId
                GROUP BY NVL(ISSUER, 'UNKNOWN')
                """, params);
        cardStatusCounts = mapDistributionLabels(cardStatusCounts, cardStatusLabels, cardStatusDuplicates);
        cardTypeCounts = mapDistributionLabels(cardTypeCounts, cardTypeLabels, cardTypeDuplicates);

        long transactionCount = queryLong("""
                SELECT COUNT(*)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                """, params);
        BigDecimal totalAmount = queryDecimal("""
                SELECT NVL(SUM(t.TX_AMOUNT), 0)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                """, params);
        BigDecimal averageAmount = queryDecimal("""
                SELECT AVG(t.TX_AMOUNT)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                """, params);
        Map<String, Long> txTypeCounts = loadDistribution("""
                SELECT NVL(t.TX_TYPE, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                GROUP BY NVL(t.TX_TYPE, 'UNKNOWN')
                """, params);
        txTypeCounts = mapDistributionLabels(txTypeCounts, transactionTypeLabels, transactionTypeDuplicates);
        List<UserDashboardResponseDTO.DateCountDTO> dailyCounts = loadUserDateCounts(params);
        List<UserDashboardResponseDTO.RecentTransactionDTO> recentTransactions = jdbcTemplate.query("""
                SELECT t.TX_ID, t.CREATED_AT, t.TX_AMOUNT, t.MERCHANT_CAT, t.LOCATION,
                       t.TARGET_ACCOUNT_NUMBER, t.DESCRIPTION
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE a.USER_INNER_ID = :userId
                ORDER BY t.CREATED_AT DESC
                FETCH NEXT 10 ROWS ONLY
                """, params, (rs, rowNum) -> new UserDashboardResponseDTO.RecentTransactionDTO(
                rs.getLong("TX_ID"),
                toLocalDateTime(rs.getTimestamp("CREATED_AT")),
                rs.getBigDecimal("TX_AMOUNT"),
                rs.getString("MERCHANT_CAT"),
                rs.getString("LOCATION"),
                rs.getString("TARGET_ACCOUNT_NUMBER"),
                rs.getString("DESCRIPTION")
        ));

        long detectedCount = queryLong("""
                SELECT COUNT(*)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                """, params);
        long fraudCount = queryLong("""
                SELECT COUNT(*)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                  AND d.IS_FRAUD = 1
                """, params);
        LocalDateTime latestDetectionAt = queryTimestamp("""
                SELECT MAX(d.DETECTED_AT)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                """, params);

        UserDashboardResponseDTO.CardSummaryDTO cards = new UserDashboardResponseDTO.CardSummaryDTO(
                cardCount,
                averageCardsPerUser,
                cardStatusCounts,
                cardTypeCounts,
                cardIssuerCounts
        );
        UserDashboardResponseDTO.TransactionSummaryDTO transactions = new UserDashboardResponseDTO.TransactionSummaryDTO(
                transactionCount,
                totalAmount,
                averageAmount,
                txTypeCounts,
                dailyCounts,
                recentTransactions
        );
        UserDashboardResponseDTO.DetectionSummaryDTO detections = new UserDashboardResponseDTO.DetectionSummaryDTO(
                detectedCount,
                fraudCount,
                safeRate(fraudCount, detectedCount),
                latestDetectionAt
        );

        return new UserDashboardResponseDTO(profile, accounts, cards, transactions, detections);
    }

    private List<UserDashboardResponseDTO.DateCountDTO> loadUserDateCounts(
            MapSqlParameterSource params
    ) {
        return jdbcTemplate.query("""
                SELECT TRUNC(t.CREATED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                GROUP BY TRUNC(t.CREATED_AT)
                ORDER BY TRUNC(t.CREATED_AT)
                """, params, (rs, rowNum) -> new UserDashboardResponseDTO.DateCountDTO(
                toLocalDate(rs.getTimestamp(KEY_DATE)),
                rs.getLong(COUNT_VALUE)
        ));
    }

    /**
     * 사용자 거래 범위 내 평균 사기 확률을 조회한다.
     */
    private Double queryAverageFraudProbability(MapSqlParameterSource params) {
        return jdbcTemplate.queryForObject("""
                SELECT AVG(d.FRAUD_PROBABILITY)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                """, params, Double.class);
    }

    /**
     * 사용자 거래 범위 내 사기 확률 중앙값을 계산한다. 확률 중앙값 응답에 사용될 계산값을 반환한다. 데이터가 없으면 null을 반환한다.
     */
    private Double computeMedianProbability(MapSqlParameterSource params) {
        List<Double> values = jdbcTemplate.query("""
                SELECT d.FRAUD_PROBABILITY
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                JOIN FRAUD_DETECTION_RESULTS d ON d.TX_ID = t.TX_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs
                  AND t.CREATED_AT < :toTs
                  AND d.FRAUD_PROBABILITY IS NOT NULL
                ORDER BY d.FRAUD_PROBABILITY
                """, params, (rs, rowNum) -> rs.getDouble(1));
        if (values.isEmpty()) {
            return null;
        }
        int size = values.size();
        if (size % 2 == 1) {
            return values.get(size / 2);
        }
        double lower = values.get(size / 2 - 1);
        double upper = values.get(size / 2);
        return (lower + upper) / 2.0;
    }
}
