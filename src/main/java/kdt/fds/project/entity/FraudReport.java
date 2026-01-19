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

    @Column(name = "REPORTED_ACCOUNT", nullable = false)
    private String reportedAccount;

    @Column(name = "REPORTER_ID")
    private Long reporterId; // 신고자 ID (숫자형)

    @Column(name = "REASON", length = 1000)
    private String reason; // 사유 (TEXT)

    @Column(name = "REASON_CODE")
    private Integer reasonCode;

    @Builder.Default
    @Column(name = "REPORT_COUNT")
    private Integer reportCount = 1;

    @Column(name = "STATUS", length = 20)
    private String status; // 'PENDING' 등

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = "PENDING";
    }
}