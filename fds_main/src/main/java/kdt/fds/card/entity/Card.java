package kdt.fds.card.entity;

import jakarta.persistence.*;
import kdt.fds.account.entity.Account;
import kdt.fds.user.entity.User;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CARDS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_CARDS")
    @SequenceGenerator(name = "SEQ_CARDS", sequenceName = "SEQ_CARDS", allocationSize = 1)
    private Long cardId;

    // 1. 사용자 참조 (어떤 사용자의 카드인가)
    // EAGER로 설정하면 관리자 대시보드나 JS에서 유저명을 즉시 불러올 때 편리합니다.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "USER_INNER_ID", nullable = false)
    private User user;

    // 2. 계좌 참조 (결제 시 잔액 차감을 위해 연결된 계좌)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ACCOUNT_ID", nullable = false)
    private Account account;

    @Column(name = "CARD_NUMBER", unique = true, nullable = false, length = 20)
    private String cardNumber; // 포맷: 1234-5678-1234-5678

    @Column(name = "CARD_TYPE", nullable = false)
    private String cardType; // "CHECK", "CREDIT"

    // issuer(발급사) 필드 유지 (FDS BANK 등)
    @Builder.Default
    private String issuer = "FDS BANK";

    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, LOST, TERMINATED

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}