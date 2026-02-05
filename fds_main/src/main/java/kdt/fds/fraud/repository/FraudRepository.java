package kdt.fds.fraud.repository;

import kdt.fds.fraud.dto.FraudDetailDTO;
import kdt.fds.fraud.entity.FraudDetectionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FraudRepository extends JpaRepository<FraudDetectionResult, Long> {

    Optional<FraudDetectionResult> findByTxId(Long txId);

    /**
     * [해결]
     * 1. DTO 경로를 통합된 패키지인 kdt.fds.fraud.dto.FraudDetailDTO로 수정
     * 2. User 엔티티 필드명: userName -> name
     * 3. Transaction 엔티티 필드명: targetValue -> targetAccountNumber, txAmount -> amount, txTimestamp -> createdAt
     */
    @Query("""
        SELECT new kdt.fds.fraud.dto.FraudDetailDTO(
            f.id,
            t.txId,
            u.name,
            u.userId,
            t.sourceValue,
            t.targetAccountNumber,
            CAST(t.amount AS double),
            f.probability,
            f.isFraud,
            f.engine,
            tf.vFeatures,
            t.createdAt
        )
        FROM FraudDetectionResult f
        JOIN Transaction t ON f.txId = t.txId
        LEFT JOIN User u ON t.user = u
        LEFT JOIN TransactionFeature tf ON t.txId = tf.txId
        ORDER BY f.id DESC
    """)
    List<FraudDetailDTO> findAllWithDetails();

    // 특정 거래 ID에 해당하는 모든 탐지 결과 삭제
    void deleteByTxId(Long txId);
}
