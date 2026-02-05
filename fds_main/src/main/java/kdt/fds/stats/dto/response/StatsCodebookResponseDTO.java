package kdt.fds.stats.dto.response;

import java.time.LocalDateTime;
import kdt.fds.stats.entity.StatsCodebook;

/**
 * 이 파일은 코드북 응답 DTO 레코드 파일이다.
 * 코드북 엔티티를 API 응답용으로 변환한 데이터를 담는다.
 */
public record StatsCodebookResponseDTO(
        Long codebookId,
        String codeType,
        String codeKey,
        String displayName,
        String description,
        Integer sortOrder,
        boolean active,
        String metaJson,
        String createdBy,
        String updatedBy,
        String changeReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * 엔티티를 DTO로 변환하는 정적 팩토리 메서드이다.
     * 엔티티의 모든 필드를 DTO로 매핑한다.
     */
    public static StatsCodebookResponseDTO from(StatsCodebook entity) {
        return new StatsCodebookResponseDTO(
                entity.getCodebookId(),
                entity.getCodeType(),
                entity.getCodeKey(),
                entity.getDisplayName(),
                entity.getDescription(),
                entity.getSortOrder(),
                entity.isActive(),
                entity.getMetaJson(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                entity.getChangeReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
