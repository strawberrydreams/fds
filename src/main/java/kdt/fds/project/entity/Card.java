package kdt.fds.project.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CARDS")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_CARDS")
    @SequenceGenerator(name = "SEQ_CARDS", sequenceName = "SEQ_CARDS", allocationSize = 1)
    private Long cardId;

    // 1. 사용자 참조 (어떤 사용자의 카드인가)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_INNER_ID", nullable = false)
    private User user;

    // 2. 계좌 참조 (어떤 계좌와 연결된 카드인가)
    // 이 관계를 통해 계좌번호(accountNumber)와 비밀번호(password)에 접근합니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ACCOUNT_ID", nullable = false)
    private Account account;

    @Column(name = "CARD_NUMBER", unique = true, nullable = false, length = 20)
    private String cardNumber; // 예: 1234-5678-1234-5678

    @Column(name = "CARD_TYPE", nullable = false)
    private String cardType; // "CHECK" (체크카드), "CREDIT" (신용카드)

    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}