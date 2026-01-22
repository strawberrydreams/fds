package kdt.fds.stats.dto.request;

import jakarta.validation.constraints.AssertTrue;

import java.time.LocalDate;

/**
 * 이 파일은 스냅샷 생성 요청 DTO 레코드 파일이다.
 * record를 사용해 DTO를 불변이면서 간결하게 유지한다.
 */
public record StatsSnapshotGenerateRequestDTO(
        LocalDate fromDate,

        LocalDate toDate,
        Boolean forceRebuild
) {
    /**
     * 선택 필드의 기본값을 적용한다.
     * rebuild 플래그가 null이면 false로 처리한다.
     */
    public StatsSnapshotGenerateRequestDTO {
        if (forceRebuild == null) forceRebuild = false;
    }

    /**
     * 날짜 범위가 올바른 순서인지 검증한다.
     * null은 다른 제약에서 검증하도록 허용한다.
     */
    @AssertTrue(message = "fromDate must be <= toDate")
    public boolean isDateRangeValid() {
        if (fromDate == null || toDate == null) {
            return true;
        }

        return !fromDate.isAfter(toDate);
    }
}
