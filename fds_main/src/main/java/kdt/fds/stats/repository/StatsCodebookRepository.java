package kdt.fds.stats.repository;

import java.util.List;
import java.util.Optional;
import kdt.fds.stats.entity.StatsCodebook;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 이 파일은 코드북 리포지토리 파일이다.
 * 검증 및 UI 사용을 위해 타입과 키 조회를 지원한다.
 */
public interface StatsCodebookRepository extends JpaRepository<StatsCodebook, Long> {
    // 정렬 순서와 키로 정렬된 코드북 엔트리를 조회한다. 지정한 코드 타입의 모든 엔트리를 반환한다.
    List<StatsCodebook> findByCodeTypeOrderBySortOrderAscCodeKeyAsc(String codeType);
    // 타입과 키로 단일 코드북 엔트리를 조회한다. 일치 항목이 없으면 빈 값을 반환한다.
    Optional<StatsCodebook> findByCodeTypeAndCodeKey(String codeType, String codeKey);
}
