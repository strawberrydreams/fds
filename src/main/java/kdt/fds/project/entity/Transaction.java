package kdt.fds.project.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TRANSACTIONS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_TRANSACTIONS")
    @SequenceGenerator(name = "SEQ_TRANSACTIONS", sequenceName = "SEQ_TRANSACTIONS", allocationSize = 1)
    private Long txId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ACCOUNT_ID")
    private Account account;

    @Column(name = "TX_TYPE")
    private String txType; // DEPOSIT, WITHDRAW, TRANSFER_IN, TRANSFER_OUT

    private Long amount;

    @Column(name = "BALANCE_AFTER_TX")
    private Long balanceAfterTx;

    // 이 필드가 없어서 빨간 줄이 뜨는 것입니다!
    @Column(name = "DESCRIPTION")
    private String description;

    private String targetAccountNumber;
    @Builder.Default
    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt = LocalDateTime.now();
}