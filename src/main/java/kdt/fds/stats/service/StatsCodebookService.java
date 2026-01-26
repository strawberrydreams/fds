package kdt.fds.stats.service;

import java.util.List;
import kdt.fds.stats.dto.request.StatsCodebookRequestDTO;
import kdt.fds.stats.dto.response.StatsCodebookResponseDTO;
import kdt.fds.stats.entity.StatsCodebook;
import kdt.fds.stats.repository.StatsCodebookRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 이 파일은 통계 코드북 서비스 파일이다.
 * 코드북 필드를 검증하고 저장소 저장을 처리한다.
 */
@Service
@Transactional
public class StatsCodebookService {
    private final StatsCodebookRepository statsCodebookRepository;

    public StatsCodebookService(StatsCodebookRepository statsCodebookRepository) {
        this.statsCodebookRepository = statsCodebookRepository;
    }

    /**
     * 유형 필터를 적용해 코드북 엔트리를 조회한다.
     * 일관된 결과를 위해 안정적인 정렬을 적용한다.
     */
    @Transactional(readOnly = true)
    public List<StatsCodebookResponseDTO> list(String codeType) {
        List<StatsCodebook> entities;
        if (codeType == null || codeType.isBlank()) {
            entities = statsCodebookRepository.findAll(
                    Sort.by("codeType").ascending()
                            .and(Sort.by("sortOrder").ascending())
                            .and(Sort.by("codeKey").ascending())
            );
        } else {
            String normalized = codeType.trim();
            entities = statsCodebookRepository.findByCodeTypeOrderBySortOrderAscCodeKeyAsc(normalized);
        }
        return entities.stream()
                .map(StatsCodebookResponseDTO::from)
                .toList();
    }

    /**
     * id로 단일 코드북 엔트리를 반환한다.
     * 엔트리가 없으면 404 응답을 발생시킨다.
     */
    @Transactional(readOnly = true)
    public StatsCodebookResponseDTO get(Long codebookId) {
        StatsCodebook entity = statsCodebookRepository.findById(codebookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Codebook not found"));
        return StatsCodebookResponseDTO.from(entity);
    }

    /**
     * 요청 payload로 새 코드북 엔트리를 생성한다.
     * 엔티티 저장 전에 필수 필드를 검증한다.
     */
    public StatsCodebookResponseDTO create(StatsCodebookRequestDTO request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codebook request is required");
        }

        StatsCodebook entity = new StatsCodebook();
        applyAllFields(entity, request);
        applyCreateAudit(entity, request);
        StatsCodebook saved = save(entity);
        return StatsCodebookResponseDTO.from(saved);
    }

    /**
     * id로 기존 코드북 엔트리를 수정한다.
     * 입력을 검증하고 변경 가능한 필드를 모두 적용한다.
     */
    public StatsCodebookResponseDTO update(Long codebookId, StatsCodebookRequestDTO request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codebook request is required");
        }

        StatsCodebook entity = statsCodebookRepository.findById(codebookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Codebook not found"));

        applyAllFields(entity, request);
        applyUpdateAudit(entity, request);
        StatsCodebook saved = save(entity);
        return StatsCodebookResponseDTO.from(saved);
    }

    /**
     * id로 코드북 엔트리를 삭제한다.
     * 엔트리가 없으면 404 응답으로 실패한다.
     */
    public void delete(Long codebookId) {
        if (!statsCodebookRepository.existsById(codebookId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Codebook not found");
        }
        statsCodebookRepository.deleteById(codebookId);
    }

    private void applyAllFields(StatsCodebook entity, StatsCodebookRequestDTO request) {
        entity.setCodeType(request.codeType().trim());
        entity.setCodeKey(request.codeKey().trim());
        entity.setDisplayName(request.displayName().trim());
        entity.setDescription(request.description());
        entity.setSortOrder(request.sortOrder());
        entity.setActive(request.active());
        entity.setMetaJson(request.metaJson());
    }

    private void applyCreateAudit(StatsCodebook entity, StatsCodebookRequestDTO request) {
        String actor = resolveActor();
        if (entity.getCreatedBy() == null || entity.getCreatedBy().isBlank()) {
            entity.setCreatedBy(actor);
        }
        entity.setUpdatedBy(actor);
        entity.setChangeReason(normalizeReason(request.changeReason(), true));
    }

    private void applyUpdateAudit(StatsCodebook entity, StatsCodebookRequestDTO request) {
        String actor = resolveActor();
        entity.setUpdatedBy(actor);
        entity.setChangeReason(normalizeReason(request.changeReason(), false));
    }

    private String resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "unknown";
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return "unknown";
        }
        return name;
    }

    private String normalizeReason(String reason, boolean isCreate) {
        if (reason == null || reason.isBlank()) {
            return isCreate ? "신규 등록" : "관리자 수정";
        }
        return reason.trim();
    }

    private StatsCodebook save(StatsCodebook entity) {
        try {
            return statsCodebookRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Codebook already exists", ex);
        }
    }
}
