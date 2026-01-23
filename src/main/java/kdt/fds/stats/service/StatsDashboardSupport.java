package kdt.fds.stats.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kdt.fds.stats.vo.StatsDateRange;
import kdt.fds.stats.vo.StatsRangeType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 대시보드 쿼리 서비스(User/Admin)를 보조하는 공통 메서드의 모음
 */
abstract class StatsDashboardSupport {
    protected static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    protected static final int TOP_LIMIT = 5;
    protected static final String KEY_NAME = "KEY_NAME";
    protected static final String COUNT_VALUE = "COUNT_VALUE";
    protected static final String AMOUNT_VALUE = "AMOUNT_VALUE";
    protected static final String KEY_DATE = "KEY_DATE";

    protected final NamedParameterJdbcTemplate jdbcTemplate;

    protected StatsDashboardSupport(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 조회 기간 타입을 날짜 범위로 변환한다. 조회 기간이 null이면 디폴트값을 사용한다.
     */
    protected StatsDateRange resolveRange(StatsRangeType rangeType) {
        StatsRangeType normalized = rangeType == null ? StatsRangeType.LAST_7_DAYS : rangeType;
        return normalized.toDateRange(DEFAULT_ZONE);
    }

    /**
     * 시작/종료일 요청을 보정해 날짜 범위를 만든다. 요청이 null이면 디폴트값을 사용한다.
     */
    protected StatsDateRange resolveRange(LocalDate fromDate, LocalDate toDate) {
        StatsRangeType defaultRange = StatsRangeType.LAST_7_DAYS;
        StatsDateRange fallback = defaultRange.toDateRange(DEFAULT_ZONE);
        if (fromDate == null && toDate == null) {
            return fallback;
        }
        LocalDate from = fromDate == null ? fallback.fromDate() : fromDate;
        LocalDate to = toDate == null ? from : toDate;
        if (from.isAfter(to)) {
            from = to;
        }
        return new StatsDateRange(from, to);
    }

    protected Map<String, Long> loadDistribution(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = normalizeText(Objects.toString(row.get(KEY_NAME), "UNKNOWN"));
            if (key == null) {
                key = "UNKNOWN";
            }
            result.put(key, toLong(row.get(COUNT_VALUE)));
        }
        return result;
    }

    protected long queryLong(String sql, MapSqlParameterSource params) {
        Long value = jdbcTemplate.queryForObject(sql, params, Long.class);
        return value == null ? 0L : value;
    }

    protected BigDecimal queryDecimal(String sql, MapSqlParameterSource params) {
        return jdbcTemplate.queryForObject(sql, params, BigDecimal.class);
    }

    protected LocalDateTime queryTimestamp(String sql, MapSqlParameterSource params) {
        Timestamp value = jdbcTemplate.queryForObject(sql, params, Timestamp.class);
        return value == null ? null : value.toLocalDateTime();
    }

    protected LocalDate toLocalDate(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toLocalDate();
    }

    protected LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    protected BigDecimal safeRate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    protected String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    protected long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    /**
     * 코드 타입에 해당하는 코드북 레이블 맵을 로드한다.
     * CODE_KEY를 키로, DISPLAY_NAME을 값으로 매핑한다.
     */
    protected Map<String, String> loadCodebookLabels(String codeType) {
        if (codeType == null || codeType.isBlank()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("codeType", codeType.trim());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT CODE_KEY, DISPLAY_NAME
                FROM STATS_CODEBOOK
                WHERE CODE_TYPE = :codeType
                """, params);
        Map<String, String> labels = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String codeKey = normalizeText(Objects.toString(row.get("CODE_KEY"), null));
            if (codeKey == null) {
                continue;
            }
            String displayName = normalizeText(Objects.toString(row.get("DISPLAY_NAME"), null));
            labels.put(codeKey, displayName == null ? codeKey : displayName);
        }
        return labels;
    }

    /**
     * 레이블 맵에서 중복된 레이블 값을 찾는다.
     * 동일한 DISPLAY_NAME이 여러 CODE_KEY에 매핑된 경우 해당 레이블을 반환한다.
     */
    protected Set<String> findDuplicateLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return Set.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String label : labels.values()) {
            String normalized = normalizeText(label);
            if (normalized == null) {
                continue;
            }
            counts.merge(normalized, 1, Integer::sum);
        }
        Set<String> duplicates = new HashSet<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 1) {
                duplicates.add(entry.getKey());
            }
        }
        return duplicates;
    }

    /**
     * 코드 값을 표시 레이블로 변환한다.
     * 중복 레이블이면 코드 값을 괄호로 붙여 구분한다.
     */
    protected String resolveCodeLabel(String code, Map<String, String> labels, Set<String> duplicateLabels) {
        String normalizedCode = normalizeText(code);
        if (normalizedCode == null) {
            return code;
        }
        String label = labels.get(normalizedCode);
        if (label == null) {
            return code;
        }
        String normalizedLabel = normalizeText(label);
        if (normalizedLabel == null) {
            return code;
        }
        if (duplicateLabels.contains(normalizedLabel)) {
            return normalizedLabel + " (" + normalizedCode + ")";
        }
        return normalizedLabel;
    }

    /**
     * 분포 맵의 키를 코드북 레이블로 변환한다.
     * 동일 레이블로 변환되는 항목은 값을 합산한다.
     */
    protected Map<String, Long> mapDistributionLabels(
            Map<String, Long> distribution,
            Map<String, String> labels,
            Set<String> duplicateLabels
    ) {
        if (distribution == null || distribution.isEmpty() || labels == null || labels.isEmpty()) {
            return distribution;
        }
        Map<String, Long> mapped = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : distribution.entrySet()) {
            String label = resolveCodeLabel(entry.getKey(), labels, duplicateLabels);
            mapped.merge(label, entry.getValue(), Long::sum);
        }
        return mapped;
    }
}
