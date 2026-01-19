package kdt.fds.project.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "FRAUD_DETECTION_RESULTS") // schema 제거
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FraudDetectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "det_seq")
    @SequenceGenerator(
            name = "det_seq",
            sequenceName = "SEQ_DETECTION_ID", // FDS_ADMIN. 제거
            allocationSize = 1
    )
    @Column(name = "DETECTION_ID")
    private Long id;

    @Column(name = "TX_ID", nullable = false)
    private Long txId;

    @Column(name = "FRAUD_PROBABILITY", nullable = false)
    private Double probability;

    @Column(name = "IS_FRAUD", nullable = false)
    private Integer isFraud; // 0: 정상, 1: 사기

    @Column(name = "DETECTED_ENGINE", length = 50)
    private String engine;

    @Column(name = "THRESHOLD_VALUE", nullable = false)
    private Double thresholdValue;

    @Column(name = "DETECTED_AT")
    private LocalDateTime detectedAt;

    /**
     * 화면 출력용 (DB 컬럼 아님)
     * User 엔티티의 필드명이 'name'이므로 이에 맞춰 관리합니다.
     */
    @Transient
    private String userId;

    @Transient
    private String name; // userName 대신 name 사용 (User 엔티티와 일치)

    // 비즈니스 로직
    public void updateManualDecision(boolean fraud) {
        this.isFraud = fraud ? 1 : 0;
        this.detectedAt = LocalDateTime.now();
    }
}