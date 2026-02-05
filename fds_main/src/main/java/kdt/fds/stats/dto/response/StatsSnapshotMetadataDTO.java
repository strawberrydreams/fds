package kdt.fds.stats.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 이 파일은 스냅샷 메타데이터 응답 DTO 레코드 파일이다.
 * 히스토리 목록에서 스냅샷 식별 정보를 제공한다.
 */
public record StatsSnapshotMetadataDTO(
        String snapshotId,
        String scope,
        LocalDate fromDate,
        LocalDate toDate,
        LocalDateTime generatedAt,
        String filename
) { }
