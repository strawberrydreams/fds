package kdt.fds.stats.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
 * 사용자 대시보드 집계 서비스
 * FRAUD_REPORTS 테이블을 참조하며, 계좌번호 형식(하이픈 등)에 상관없이 매칭되도록 보정됨.
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
     * [KPI 요약] 사용자 거래 및 신고(FRAUD_REPORTS) 기반 지표 집계
     */
    public UserSummaryResponseDTO getUserSummary(Long userId, StatsRangeType rangeType) {
        StatsDateRange range = resolveRange(rangeType);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("fromTs", range.fromTimestamp())
                .addValue("toTs", range.toExclusiveTimestamp());

        // 1. 거래 통계 (기존 유지)
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

        // 2. [완전 수정] 하이픈이나 공백에 상관없이 매칭되도록 REGEXP_REPLACE 사용
        // r.REPORTED_ACCOUNT 와 a.ACCOUNT_NUMBER에서 숫자가 아닌 모든 문자를 제거하고 비교합니다.
        long detectedCount = queryLong("""
                SELECT COUNT(*)
                FROM FRAUD_REPORTS r
                JOIN ACCOUNTS a ON REGEXP_REPLACE(r.REPORTED_ACCOUNT, '[^0-9]', '') = REGEXP_REPLACE(a.ACCOUNT_NUMBER, '[^0-9]', '')
                WHERE a.USER_INNER_ID = :userId
                  AND r.CREATED_AT >= :fromTs
                  AND r.CREATED_AT < :toTs
                """, params);

        long fraudCount = queryLong("""
                SELECT COUNT(*)
                FROM FRAUD_REPORTS r
                JOIN ACCOUNTS a ON REGEXP_REPLACE(r.REPORTED_ACCOUNT, '[^0-9]', '') = REGEXP_REPLACE(a.ACCOUNT_NUMBER, '[^0-9]', '')
                WHERE a.USER_INNER_ID = :userId
                  AND r.STATUS = 'PENDING'
                  AND r.CREATED_AT >= :fromTs
                  AND r.CREATED_AT < :toTs
                """, params);

        // 3. 최신 시각 정보
        LocalDateTime latestTransactionAt = queryTimestamp("""
                SELECT MAX(t.CREATED_AT)
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE a.USER_INNER_ID = :userId
                """, params);

        LocalDateTime latestDetectionAt = queryTimestamp("""
                SELECT MAX(r.CREATED_AT)
                FROM FRAUD_REPORTS r
                JOIN ACCOUNTS a ON REGEXP_REPLACE(r.REPORTED_ACCOUNT, '[^0-9]', '') = REGEXP_REPLACE(a.ACCOUNT_NUMBER, '[^0-9]', '')
                WHERE a.USER_INNER_ID = :userId
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
                0.0,
                0.0,
                latestTransactionAt,
                latestDetectionAt
        );
    }

    /**
     * [상세 대시보드] 데이터 구성
     */
    public UserDashboardResponseDTO getUserDashboard(Long userId, StatsRangeType rangeType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserSummaryResponseDTO summary = getUserSummary(userId, rangeType);

        StatsDateRange range = resolveRange(rangeType);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("fromTs", range.fromTimestamp())
                .addValue("toTs", range.toExclusiveTimestamp());

        UserDashboardResponseDTO.UserProfileDTO profile = new UserDashboardResponseDTO.UserProfileDTO(
                user.getId(), user.getUserId(), user.getName()
        );

        List<UserDashboardResponseDTO.AccountDTO> accounts = jdbcTemplate.query("""
                SELECT ACCOUNT_ID, ACCOUNT_NUMBER, STATUS, BALANCE, CREATED_AT
                FROM ACCOUNTS
                WHERE USER_INNER_ID = :userId
                ORDER BY CREATED_AT DESC
                """, params, (rs, rowNum) -> new UserDashboardResponseDTO.AccountDTO(
                rs.getLong("ACCOUNT_ID"), rs.getString("ACCOUNT_NUMBER"),
                rs.getString("STATUS"), rs.getBigDecimal("BALANCE"),
                toLocalDateTime(rs.getTimestamp("CREATED_AT"))
        ));

        // 카드 및 거래 요약은 기존 로직 유지
        UserDashboardResponseDTO.CardSummaryDTO cardSummary = new UserDashboardResponseDTO.CardSummaryDTO(
                queryLong("SELECT COUNT(*) FROM CARDS WHERE USER_INNER_ID = :userId", params),
                BigDecimal.ONE,
                loadDistribution("SELECT NVL(STATUS, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE FROM CARDS WHERE USER_INNER_ID = :userId GROUP BY STATUS", params),
                loadDistribution("SELECT NVL(CARD_TYPE, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE FROM CARDS WHERE USER_INNER_ID = :userId GROUP BY CARD_TYPE", params),
                loadDistribution("SELECT NVL(ISSUER, 'UNKNOWN') AS KEY_NAME, COUNT(*) AS COUNT_VALUE FROM CARDS WHERE USER_INNER_ID = :userId GROUP BY ISSUER", params)
        );

        List<UserDashboardResponseDTO.RecentTransactionDTO> recentTransactions = jdbcTemplate.query("""
                SELECT t.TX_ID, t.CREATED_AT, t.TX_AMOUNT, t.MERCHANT_CAT, t.LOCATION,
                       t.TARGET_ACCOUNT_NUMBER, t.DESCRIPTION
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE a.USER_INNER_ID = :userId
                ORDER BY t.CREATED_AT DESC
                FETCH NEXT 10 ROWS ONLY
                """, params, (rs, rowNum) -> new UserDashboardResponseDTO.RecentTransactionDTO(
                rs.getLong("TX_ID"), toLocalDateTime(rs.getTimestamp("CREATED_AT")),
                rs.getBigDecimal("TX_AMOUNT"), rs.getString("MERCHANT_CAT"),
                rs.getString("LOCATION"), rs.getString("TARGET_ACCOUNT_NUMBER"),
                rs.getString("DESCRIPTION")
        ));

        UserDashboardResponseDTO.TransactionSummaryDTO transactionSummary = new UserDashboardResponseDTO.TransactionSummaryDTO(
                summary.transactionCount(), summary.totalAmount(), summary.averageAmount(),
                new java.util.LinkedHashMap<>(), loadUserDateCounts(params), recentTransactions
        );

        // 탐지 요약 정보 주입
        UserDashboardResponseDTO.DetectionSummaryDTO detections = new UserDashboardResponseDTO.DetectionSummaryDTO(
                summary.detectedCount(),
                summary.fraudCount(),
                summary.fraudRate(),
                summary.latestDetectionAt()
        );

        return new UserDashboardResponseDTO(profile, accounts, cardSummary, transactionSummary, detections);
    }

    private List<UserDashboardResponseDTO.DateCountDTO> loadUserDateCounts(MapSqlParameterSource params) {
        return jdbcTemplate.query("""
                SELECT TRUNC(t.CREATED_AT) AS KEY_DATE, COUNT(*) AS COUNT_VALUE
                FROM TRANSACTIONS t
                JOIN ACCOUNTS a ON a.ACCOUNT_ID = t.ACCOUNT_ID
                WHERE a.USER_INNER_ID = :userId
                  AND t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                GROUP BY TRUNC(t.CREATED_AT) ORDER BY TRUNC(t.CREATED_AT)
                """, params, (rs, rowNum) -> new UserDashboardResponseDTO.DateCountDTO(
                toLocalDate(rs.getTimestamp("KEY_DATE")), rs.getLong("COUNT_VALUE")
        ));
    }
}