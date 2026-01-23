package kdt.fds.stats.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.io.IOException;
import java.sql.Timestamp;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kdt.fds.stats.config.StatsSnapshotProperties;
import kdt.fds.stats.dto.request.StatsSnapshotGenerateRequestDTO;
import kdt.fds.stats.dto.response.AdminDashboardResponseDTO;
import kdt.fds.stats.dto.response.StatsSnapshotMetadataDTO;
import kdt.fds.stats.dto.response.StatsSnapshotGenerateResponseDTO;
import kdt.fds.stats.vo.StatsSnapshotScope;
import kdt.fds.stats.vo.StatsDateRange;
import lombok.NonNull;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이 파일은 스냅샷 파일 생성/조회 서비스 파일이다.
 * 주간 집계 결과를 JSON 파일로 저장하고 히스토리를 제공한다.
 */
@Service
@Transactional
public class StatsSnapshotService {
    private static final ZoneId SNAPSHOT_ZONE = ZoneId.of("Asia/Seoul");
    private static final Pattern SNAPSHOT_NAME_PATTERN =
            Pattern.compile("^(\\d{4})_(\\d{2})(\\d{2})_(?:(\\d{4})_)?(\\d{2})(\\d{2})$");
    private static final int RETENTION_DAYS = 365;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AdminStatsDashboardService adminDashboardService;
    private final StatsSnapshotProperties statsSnapshotProperties;
    private final ObjectMapper objectMapper;

    public StatsSnapshotService(
            NamedParameterJdbcTemplate jdbcTemplate,
            AdminStatsDashboardService adminDashboardService,
            StatsSnapshotProperties statsSnapshotProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminDashboardService = adminDashboardService;
        this.statsSnapshotProperties = statsSnapshotProperties;
        this.objectMapper = createObjectMapper();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * 관리자 수동 스냅샷 생성을 수행한다.
     * 주간 범위를 지정하지 않으면 직전 주간을 생성한다.
     */
    public StatsSnapshotGenerateResponseDTO generate(StatsSnapshotGenerateRequestDTO request) {
        StatsDateRange range = resolveRange(request);
        boolean forceRebuild = Boolean.TRUE.equals(request.forceRebuild());
        return generateWeeklySnapshots(range, forceRebuild);
    }

    /**
     * 자동 스케줄러용 스냅샷 생성 진입점이다.
     * 지정된 주간 범위를 JSON 파일로 저장한다.
     */
    public StatsSnapshotGenerateResponseDTO generateWeeklySnapshots(
            StatsDateRange range,
            boolean forceRebuild
    ) {
        LocalDateTime generatedAt = LocalDateTime.now(SNAPSHOT_ZONE);
        SnapshotResult generalResult = writeGeneralSnapshot(range, generatedAt, forceRebuild);
        SnapshotResult businessResult = writeBusinessSnapshot(range, generatedAt, forceRebuild);
        cleanupOldGeneralSnapshots();

        return new StatsSnapshotGenerateResponseDTO(
                range.fromDate(),
                range.toDate(),
                generalResult.filename(),
                businessResult.filename(),
                generatedAt
        );
    }

    /**
     * 스냅샷 히스토리 목록을 반환한다.
     * 스코프별 파일 메타데이터를 정렬해 제공한다.
     */
    @Transactional(readOnly = true)
    public List<StatsSnapshotMetadataDTO> listSnapshots(StatsSnapshotScope scope) {
        Path directory = resolveDirectory(scope);
        if (!Files.exists(directory)) {
            return List.of();
        }
        List<StatsSnapshotMetadataDTO> results = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> parseMetadata(scope, path).ifPresent(results::add));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read snapshot directory", ex);
        }
        results.sort(Comparator.comparing(StatsSnapshotMetadataDTO::fromDate).reversed());
        return results;
    }

    /**
     * 검증된 파일명으로 스냅샷 상세 JSON을 반환한다.
     * 컨트롤러에서 allowlist 검증 후 호출해야 한다.
     * Object로 반환하여 Jackson이 올바르게 직렬화하도록 한다.
     */
    @Transactional(readOnly = true)
    public Object getSnapshotDetailByFilename(StatsSnapshotScope scope, String filename) {
        Path filePath = resolveDirectory(scope).resolve(filename);
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("Snapshot file not found: " + filename);
        }
        try {
            return objectMapper.readValue(filePath.toFile(), Object.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read snapshot file", ex);
        }
    }

    @Transactional(readOnly = true)
    public Path getSnapshotFilePath(StatsSnapshotScope scope, String filename) {
        Path filePath = resolveDirectory(scope).resolve(filename);
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("Snapshot file not found: " + filename);
        }
        return filePath;
    }

    private SnapshotResult writeGeneralSnapshot(
            StatsDateRange range,
            LocalDateTime generatedAt,
            boolean forceRebuild
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromTs", range.fromDate().atStartOfDay())
                .addValue("toTs", range.toDate().plusDays(1).atStartOfDay());

        long transactionCount = queryLong("""
                SELECT COUNT(*)
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                """, params);
        BigDecimal totalAmount = queryDecimal("""
                SELECT NVL(SUM(TX_AMOUNT), 0)
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                """, params);
        BigDecimal averageAmount = queryDecimal("""
                SELECT AVG(TX_AMOUNT)
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                """, params);
        long detectedCount = queryLong("""
                SELECT COUNT(*)
                FROM FRAUD_DETECTION_RESULTS d
                JOIN TRANSACTIONS t ON t.TX_ID = d.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                """, params);
        long fraudCount = queryLong("""
                SELECT COUNT(*)
                FROM FRAUD_DETECTION_RESULTS d
                JOIN TRANSACTIONS t ON t.TX_ID = d.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                  AND d.IS_FRAUD = 1
                """, params);
        Double avgProbability = queryDouble(params);
        Double medianProbability = queryMedianProbability(params);
        LocalDateTime latestTxAt = queryTimestamp("""
                SELECT MAX(CREATED_AT)
                FROM TRANSACTIONS
                WHERE CREATED_AT >= :fromTs AND CREATED_AT < :toTs
                """, params);
        LocalDateTime latestDetectionAt = queryTimestamp("""
                SELECT MAX(d.DETECTED_AT)
                FROM FRAUD_DETECTION_RESULTS d
                JOIN TRANSACTIONS t ON t.TX_ID = d.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                """, params);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", StatsSnapshotScope.GENERAL.name());
        payload.put("fromDate", range.fromDate());
        payload.put("toDate", range.toDate());
        payload.put("generatedAt", generatedAt);

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("transactionCount", transactionCount);
        kpi.put("totalAmount", totalAmount);
        kpi.put("averageAmount", averageAmount);
        kpi.put("detectedCount", detectedCount);
        kpi.put("detectedRate", safeRate(detectedCount, transactionCount));
        kpi.put("fraudCount", fraudCount);
        kpi.put("fraudRate", safeRate(fraudCount, detectedCount));
        kpi.put("averageFraudProbability", avgProbability);
        kpi.put("medianFraudProbability", medianProbability);
        kpi.put("latestTransactionAt", latestTxAt);
        kpi.put("latestDetectionAt", latestDetectionAt);
        payload.put("kpi", kpi);

        Path targetPath = resolveSnapshotPath(StatsSnapshotScope.GENERAL, range);
        writeSnapshotFile(targetPath, payload, forceRebuild);
        return new SnapshotResult(targetPath.getFileName().toString());
    }

    private SnapshotResult writeBusinessSnapshot(
            StatsDateRange range,
            LocalDateTime generatedAt,
            boolean forceRebuild
    ) {
        AdminDashboardResponseDTO dashboard =
                adminDashboardService.getAdminDashboard(range.fromDate(), range.toDate());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", StatsSnapshotScope.BUSINESS.name());
        payload.put("fromDate", range.fromDate());
        payload.put("toDate", range.toDate());
        payload.put("generatedAt", generatedAt);
        payload.put("dashboard", dashboard);

        Path targetPath = resolveSnapshotPath(StatsSnapshotScope.BUSINESS, range);
        writeSnapshotFile(targetPath, payload, forceRebuild);
        return new SnapshotResult(targetPath.getFileName().toString());
    }

    private void writeSnapshotFile(Path targetPath, Map<String, Object> payload, boolean forceRebuild) {
        if (!forceRebuild && Files.exists(targetPath)) {
            return;
        }
        try {
            Files.createDirectories(targetPath.getParent());
            // 동일 주차 스냅샷은 덮어쓰기 정책을 적용한다.
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(targetPath.toFile(), payload);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write snapshot file", ex);
        }
    }

    private void cleanupOldGeneralSnapshots() {
        LocalDateTime cutoff = LocalDateTime.now(SNAPSHOT_ZONE).minusDays(RETENTION_DAYS);
        Path directory = resolveDirectory(StatsSnapshotScope.GENERAL);
        if (!Files.exists(directory)) {
            return;
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
                    if (!file.getFileName().toString().endsWith(".json")) {
                        return FileVisitResult.CONTINUE;
                    }
                    LocalDateTime modifiedAt = LocalDateTime.ofInstant(
                            attrs.lastModifiedTime().toInstant(),
                            SNAPSHOT_ZONE
                    );
                    if (modifiedAt.isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to cleanup old snapshots", ex);
        }
    }

    private StatsDateRange resolveRange(StatsSnapshotGenerateRequestDTO request) {
        if (request == null) {
            return resolveLastWeek();
        }
        if (request.fromDate() == null || request.toDate() == null) {
            return resolveLastWeek();
        }
        LocalDate from = request.fromDate();
        LocalDate to = request.toDate();
        if (from.isAfter(to)) {
            from = to;
        }
        return new StatsDateRange(from, to);
    }

    /**
     * 직전 주간(월~일)의 날짜 범위를 계산한다.
     * 스케줄러 및 기본 스냅샷 생성에 사용한다.
     */
    private StatsDateRange resolveLastWeek() {
        LocalDate today = LocalDate.now(SNAPSHOT_ZONE);
        LocalDate thisMonday = today.with(DayOfWeek.MONDAY);
        LocalDate lastMonday = thisMonday.minusWeeks(1);
        LocalDate lastSunday = lastMonday.plusDays(6);
        return new StatsDateRange(lastMonday, lastSunday);
    }

    private Path resolveDirectory(StatsSnapshotScope scope) {
        return Path.of(statsSnapshotProperties.getBasePath(), "weekly", scope.directoryName());
    }

    private Path resolveSnapshotPath(StatsSnapshotScope scope, StatsDateRange range) {
        String snapshotId = formatSnapshotId(range.fromDate(), range.toDate());
        return resolveSnapshotPath(scope, snapshotId);
    }

    private Path resolveSnapshotPath(StatsSnapshotScope scope, String snapshotId) {
        String filename = snapshotId + scope.fileSuffix() + ".json";
        return resolveDirectory(scope).resolve(filename);
    }

    /**
     * 날짜 범위를 스냅샷 ID 형식으로 변환한다.
     * 형식: YYYY_MMDD_YYYY_MMDD (예: 2024_1229_2025_0104)
     */
    private String formatSnapshotId(LocalDate fromDate, LocalDate toDate) {
        return "%d_%02d%02d_%d_%02d%02d".formatted(
                fromDate.getYear(),
                fromDate.getMonthValue(),
                fromDate.getDayOfMonth(),
                toDate.getYear(),
                toDate.getMonthValue(),
                toDate.getDayOfMonth()
        );
    }

    /**
     * 스냅샷 파일명에서 메타데이터를 파싱한다.
     * 스냅샷 ID 패턴(YYYY_MMDD_YYYY_MMDD)에서 날짜 범위를 추출하고,
     * 파일 수정 시간을 생성 시각으로 사용한다.
     */
    private java.util.Optional<StatsSnapshotMetadataDTO> parseMetadata(StatsSnapshotScope scope, Path path) {
        String filename = path.getFileName().toString();
        String snapshotId = filename;
        if (snapshotId.endsWith(".json")) {
            snapshotId = snapshotId.substring(0, snapshotId.length() - ".json".length());
        }
        String suffix = scope.fileSuffix();
        if (!suffix.isEmpty() && snapshotId.endsWith(suffix)) {
            snapshotId = snapshotId.substring(0, snapshotId.length() - suffix.length());
        }
        Matcher matcher = SNAPSHOT_NAME_PATTERN.matcher(snapshotId);
        if (!matcher.matches()) {
            return java.util.Optional.empty();
        }
        int fromYear = Integer.parseInt(matcher.group(1));
        int fromMonth = Integer.parseInt(matcher.group(2));
        int fromDay = Integer.parseInt(matcher.group(3));
        String toYearValue = matcher.group(4);
        int toMonth = Integer.parseInt(matcher.group(5));
        int toDay = Integer.parseInt(matcher.group(6));
        int toYear = toYearValue == null ? fromYear : Integer.parseInt(toYearValue);
        LocalDate fromDate = LocalDate.of(fromYear, fromMonth, fromDay);
        LocalDate toDate = LocalDate.of(toYear, toMonth, toDay);
        if (toYearValue == null && toDate.isBefore(fromDate)) {
            toDate = toDate.plusYears(1);
        }
        LocalDateTime generatedAt;
        try {
            generatedAt = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(path).toInstant(),
                    SNAPSHOT_ZONE
            );
        } catch (IOException ex) {
            generatedAt = null;
        }
        return java.util.Optional.of(new StatsSnapshotMetadataDTO(
                snapshotId,
                scope.name(),
                fromDate,
                toDate,
                generatedAt,
                filename
        ));
    }

    private long queryLong(String sql, MapSqlParameterSource params) {
        Long value = jdbcTemplate.queryForObject(sql, params, Long.class);
        return value == null ? 0L : value;
    }

    private BigDecimal queryDecimal(String sql, MapSqlParameterSource params) {
        return jdbcTemplate.queryForObject(sql, params, BigDecimal.class);
    }

    private Double queryDouble(MapSqlParameterSource params) {
        return jdbcTemplate.queryForObject("""
                SELECT AVG(d.FRAUD_PROBABILITY)
                FROM FRAUD_DETECTION_RESULTS d
                JOIN TRANSACTIONS t ON t.TX_ID = d.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                """, params, Double.class);
    }

    private LocalDateTime queryTimestamp(String sql, MapSqlParameterSource params) {
        Timestamp value = jdbcTemplate.queryForObject(sql, params, Timestamp.class);
        return value == null ? null : value.toLocalDateTime();
    }

    /**
     * 사기 탐지 확률의 중앙값을 계산한다.
     * 홀수 개이면 가운데 값, 짝수 개이면 중앙 두 값의 평균을 반환한다.
     */
    private Double queryMedianProbability(MapSqlParameterSource params) {
        List<Double> values = jdbcTemplate.query("""
                SELECT d.FRAUD_PROBABILITY
                FROM FRAUD_DETECTION_RESULTS d
                JOIN TRANSACTIONS t ON t.TX_ID = d.TX_ID
                WHERE t.CREATED_AT >= :fromTs AND t.CREATED_AT < :toTs
                  AND d.FRAUD_PROBABILITY IS NOT NULL
                ORDER BY d.FRAUD_PROBABILITY
                """, params, (rs, rowNum) -> rs.getDouble(1));
        if (values.isEmpty()) {
            return null;
        }
        int size = values.size();
        // 홀수 개: 정확히 가운데 값 반환
        if (size % 2 == 1) {
            return values.get(size / 2);
        }
        // 짝수 개: 중앙 두 값의 평균 반환
        double lower = values.get(size / 2 - 1);
        double upper = values.get(size / 2);
        return (lower + upper) / 2.0;
    }

    private BigDecimal safeRate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, java.math.RoundingMode.HALF_UP);
    }

    private record SnapshotResult(String filename) { }
}
