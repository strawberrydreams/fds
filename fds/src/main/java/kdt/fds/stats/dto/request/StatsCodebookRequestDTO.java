package kdt.fds.stats.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 이 파일은 코드북 요청 DTO 레코드 파일이다.
 * 코드북 생성/수정 요청 데이터를 담는다.
 */
public record StatsCodebookRequestDTO(
        @NotBlank(message = "codeType is required")
        @Size(max = 40)
        String codeType,

        @NotBlank(message = "codeKey is required")
        @Size(max = 80)
        String codeKey,

        @NotBlank(message = "displayName is required")
        @Size(max = 120)
        String displayName,

        @Size(max = 400)
        String description,

        Integer sortOrder,
        Boolean active,
        String metaJson,

        @Size(max = 200)
        String changeReason
) {
    /**
     * JSON에서 active 필드가 누락된 경우 기본값 true를 적용한다.
     */
    public StatsCodebookRequestDTO {
        if (active == null) {
            active = true;
        }
    }
}
