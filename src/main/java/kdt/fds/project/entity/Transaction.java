package kdt.fds.project.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TRANSACTIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    // [수정] DB의 TX_AMOUNT 컬럼과 명시적으로 매핑하여 NULL 에러 방지
    @Column(name = "TX_AMOUNT", nullable = false)
    private Long amount;

    @Column(name = "BALANCE_AFTER_TX")
    private Long balanceAfterTx;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "TARGET_ACCOUNT_NUMBER")
    private String targetAccountNumber;

    @Builder.Default
    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt = LocalDateTime.now();
}