package kdt.fds.project.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "BLACKLIST_ACCOUNTS")
@Getter @Setter
public class BlacklistAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BLACKLIST_ID")
    private Long id;

    @Column(name = "ACCOUNT_NUM", nullable = false, unique = true)
    private String accountNum; // 차단된 사기꾼 계좌번호

    @Column(name = "REASON")
    private String reason;     // 차단 사유

    @Column(name = "BLOCKED_AT")
    private LocalDateTime blockedAt;

    @PrePersist
    public void prePersist() {
        this.blockedAt = LocalDateTime.now();
    }
}