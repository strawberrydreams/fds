package kdt.fds.stats.controller;

import jakarta.validation.Valid;
import java.util.List;
import kdt.fds.stats.dto.request.StatsCodebookRequestDTO;
import kdt.fds.stats.dto.response.StatsCodebookResponseDTO;
import kdt.fds.stats.service.StatsCodebookService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이 파일은 통계 코드북 컨트롤러 파일이다.
 * 요청 처리를 코드북 서비스 계층에 위임한다.
 */
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/stats/codebook")
@Validated
public class StatsCodebookController {
    private final StatsCodebookService statsCodebookService;

    public StatsCodebookController(StatsCodebookService statsCodebookService) {
        this.statsCodebookService = statsCodebookService;
    }

    /**
     * 코드 타입 필터를 적용해 코드북 엔트리를 조회한다.
     * 관리자 UI를 위해 안정적인 순서로 반환한다.
     */
    @GetMapping
    public List<StatsCodebookResponseDTO> list(
            @RequestParam(required = false)
            String codeType
    ) {
        return statsCodebookService.list(codeType);
    }

    /**
     * id로 단일 코드북 엔트리를 조회한다.
     * 저장된 엔티티를 반환하거나 404 오류를 전달한다.
     */
    @GetMapping("/{codebookId}")
    public StatsCodebookResponseDTO get(
            @PathVariable
            Long codebookId
    ) {
        return statsCodebookService.get(codebookId);
    }

    /**
     * 새 코드북 엔트리를 생성한다.
     * 요청 payload를 서비스에 전달해 검증한다.
     */
    @PostMapping
    public StatsCodebookResponseDTO create(
            @Valid
            @RequestBody
            StatsCodebookRequestDTO request
    ) {
        return statsCodebookService.create(request);
    }

    /**
     * id로 기존 코드북 엔트리를 수정한다.
     * payload를 서비스에 전달해 변경 적용과 검증을 수행한다.
     */
    @PutMapping("/{codebookId}")
    public StatsCodebookResponseDTO update(
            @PathVariable
            Long codebookId,
            @Valid
            @RequestBody
            StatsCodebookRequestDTO request
    ) {
        return statsCodebookService.update(codebookId, request);
    }

    /**
     * id로 코드북 엔트리를 삭제한다.
     * 존재 여부를 확인하는 서비스로 위임한다.
     */
    @DeleteMapping("/{codebookId}")
    public void delete(
            @PathVariable
            Long codebookId
    ) {
        statsCodebookService.delete(codebookId);
    }
}
