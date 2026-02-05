package kdt.fds.stats.config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import kdt.fds.stats.service.StatsSnapshotService;
import kdt.fds.stats.vo.StatsDateRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 이 파일은 주간 스냅샷 스케줄러 파일이다.
 * 매주 월요일 00:00에 지난 주 스냅샷을 자동 생성한다.
 */
@Component
public class StatsSnapshotScheduler {
    private static final Logger log = LoggerFactory.getLogger(StatsSnapshotScheduler.class);
    private static final ZoneId SNAPSHOT_ZONE = ZoneId.of("Asia/Seoul");

    private final StatsSnapshotService statsSnapshotService;

    public StatsSnapshotScheduler(StatsSnapshotService statsSnapshotService) {
        this.statsSnapshotService = statsSnapshotService;
    }

    /**
     * 지난 주 월~일 범위를 계산해 자동 스냅샷을 생성한다.
     * 동일 주차 파일이 있어도 덮어쓰기 정책을 따른다.
     */
    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    public void generateWeeklySnapshots() {
        LocalDate today = LocalDate.now(SNAPSHOT_ZONE);
        LocalDate thisMonday = today.with(DayOfWeek.MONDAY);
        LocalDate lastMonday = thisMonday.minusWeeks(1);
        LocalDate lastSunday = lastMonday.plusDays(6);
        StatsDateRange range = new StatsDateRange(lastMonday, lastSunday);
        try {
            statsSnapshotService.generateWeeklySnapshots(range, true);
        } catch (Exception ex) {
            log.warn("Weekly snapshot generation failed: {}", ex.getMessage());
        }
    }
}
