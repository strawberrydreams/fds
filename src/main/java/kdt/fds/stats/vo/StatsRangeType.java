package kdt.fds.stats.vo;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 이 파일은 통계 기간 필터 열거형 파일이다.
 * 실시간 개인 요약에 사용할 기간을 정의한다.
 */
public enum StatsRangeType {
    TODAY,
    LAST_7_DAYS;

    public StatsDateRange toDateRange(ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        return switch (this) {
            case TODAY -> new StatsDateRange(today, today);
            case LAST_7_DAYS -> new StatsDateRange(today.minusDays(6), today);
        };
    }
}
