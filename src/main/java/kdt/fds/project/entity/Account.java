package kdt.fds.project.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction; // 최신 방식은 이 어노테이션을 권장합니다.
import java.time.LocalDateTime;

@Entity
@Table(name = "ACCOUNTS")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Hibernate 6.x 이상에서는 @SQLRestriction 사용을 강력 추천합니다.
@SQLRestriction("status = 'ACTIVE'")
public class Account {
    // ... 기존 필드 동일 ...

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_ACCOUNTS")
    @SequenceGenerator(name = "SEQ_ACCOUNTS", sequenceName = "SEQ_ACCOUNTS", allocationSize = 1)
    private Long accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_INNER_ID")
    private User user;

    @Column(name = "ACCOUNT_NUMBER", unique = true, nullable = false, length = 30)
    private String accountNumber;

    @Column(nullable = false, length = 100)
    private String password;

    @Builder.Default
    private Long balance = 0L;

    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.status = "DELETED";
    }
}