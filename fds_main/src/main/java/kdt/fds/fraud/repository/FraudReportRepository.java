package kdt.fds.fraud.repository;

import kdt.fds.fraud.entity.FraudReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FraudReportRepository extends JpaRepository<FraudReport, Long> {

    // 특정 계좌에 대해 이미 접수된 신고가 있는지 확인할 때 사용
    boolean existsByReportedAccount(String reportedAccount);
    Optional<FraudReport> findByReportedAccount(String reportedAccount);
    // (참고) 만약 나중에 DB에 TX_ID 컬럼을 추가하신다면 아래 메서드도 추가해서 쓰시면 됩니다.
    // boolean existsByTxId(Long txId);

    List<FraudReport> findAllByOrderByReportCountDesc();    // 신고 횟수 내림차순 정렬 조회
}