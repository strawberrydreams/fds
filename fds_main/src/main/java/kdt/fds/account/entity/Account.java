package kdt.fds.account.entity;

import jakarta.persistence.*;
import kdt.fds.user.entity.User;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDateTime;

@Entity
@Table(name = "ACCOUNTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
/* * Hibernate 6.x 이상에서 삭제되지 않은 계좌만 기본적으로 조회하도록 설정
 * (DB 상에 status가 'ACTIVE'인 데이터만 조회됨)
 */
@SQLRestriction("status = 'ACTIVE'")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "acc_seq")
    @SequenceGenerator(name = "acc_seq", sequenceName = "SEQ_ACCOUNTS", allocationSize = 1)
    private Long accountId;

    @ManyToOne(fetch = FetchType.EAGER) // 관리자 화면 및 JS 연동의 편의성을 위해 EAGER 유지
    @JoinColumn(name = "USER_INNER_ID")
    private User user;

    @Column(name = "ACCOUNT_NUMBER", unique = true, nullable = false, length = 30)
    private String accountNumber;

    @Column(nullable = false, length = 100)
    private String password;

    @Builder.Default
    @Column(nullable = false)
    private Long balance = 0L; // 정밀한 계산을 위해 Long 타입으로 통일

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // ==========================================
    // 비즈니스 로직
    // ==========================================

    /**
     * 출금 처리 (잔액 부족 시 예외 발생 및 트랜잭션 롤백)
     */
    public void withdraw(Long amount) {
        if (amount <= 0) {
            throw new RuntimeException("출금 금액은 0보다 커야 합니다.");
        }
        if (this.balance < amount) {
            throw new RuntimeException("잔액이 부족합니다. (현재 잔액: " + this.balance + ")");
        }
        this.balance -= amount;
    }

    /**
     * 입금 처리
     */
    public void deposit(Long amount) {
        if (amount <= 0) {
            throw new RuntimeException("입금 금액은 0보다 커야 합니다.");
        }
        this.balance += amount;
    }

    /**
     * 계좌 논리 삭제 (Soft Delete)
     */
    public void softDelete() {
        this.status = "DELETED";
    }
}