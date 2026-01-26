package kdt.fds.stats.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 이 파일은 스냅샷 생성 응답 DTO 레코드 파일이다.
 * record를 사용해 불변이고 보일러플레이트가 없는 응답 DTO를 제공한다.
 */
public record StatsSnapshotGenerateResponseDTO(
        LocalDate fromDate,
        LocalDate toDate,
        String generalSnapshotFile,
        String businessSnapshotFile,
        LocalDateTime generatedAt
) { }
