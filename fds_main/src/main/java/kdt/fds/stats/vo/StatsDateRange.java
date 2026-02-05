package kdt.fds.stats.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 이 파일은 통계 기간 범위 열거형 파일이다.
 * 시작/종료 날짜와 타임스탬프 경계를 제공한다.
 */
public record StatsDateRange(
        LocalDate fromDate,
        LocalDate toDate
) {
    public LocalDateTime fromTimestamp() {
        return fromDate.atStartOfDay();
    }

    public LocalDateTime toExclusiveTimestamp() {
        return toDate.plusDays(1).atStartOfDay();
    }
}
