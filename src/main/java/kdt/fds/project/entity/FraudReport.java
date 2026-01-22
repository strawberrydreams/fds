package kdt.fds.project.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "FRAUD_REPORTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FraudReport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rep_seq_gen")
    @SequenceGenerator(name = "rep_seq_gen", sequenceName = "SEQ_REPORT_ID", allocationSize = 1)
    @Column(name = "REPORT_ID")
    private Long reportId;

    // [핵심 수정] DB 로그에서 NULL 에러가 났던 컬럼명으로 정확히 매핑
    @Column(name = "REPORTED_ACCOUNT", nullable = false)
    private String reportedAccount;

    // 만약 DB에 ACCOUNT_NUMBER 컬럼도 존재한다면 중복 삽입 방지를 위해 추가
    @Column(name = "ACCOUNT_NUMBER")
    private String accountNumber;

    @Column(name = "REPORTER_ID")
    private Long reporterId;

    @Column(name = "REASON", length = 1000)
    private String reason;

    @Column(name = "REASON_CODE")
    private Integer reasonCode;

    @Builder.Default
    @Column(name = "REPORT_COUNT")
    private Integer reportCount = 1;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "PENDING";
        // DB의 ACCOUNT_NUMBER 컬럼에도 값을 복사하여 NULL 에러 방지
        if (this.accountNumber == null) this.accountNumber = this.reportedAccount;
    }
}