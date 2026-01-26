package kdt.fds.stats.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 이 파일은 스냅샷 저장 경로 설정 파일이다.
 * 파일 기반 스냅샷 저장 위치를 지정한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fds.snapshots")
public class StatsSnapshotProperties {
    /**
     * 스냅샷 저장 기본 경로이다.
     * 상대 경로일 경우 실행 디렉터리를 기준으로 한다.
     */
    private String basePath = "snapshots";
}
