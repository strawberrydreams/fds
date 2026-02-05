package kdt.fds.transaction.entity;

import jakarta.persistence.*;
import kdt.fds.account.entity.Account;
import kdt.fds.user.entity.User;
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

    // 1. 계좌 및 유저 연관관계 (둘 다 유지하여 조회 유연성 확보)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ACCOUNT_ID")
    private Account account; // 실제 계좌 엔티티 참조

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_INNER_ID")
    private User user; // 보안 및 통계용 유저 참조

    @Column(name = "USER_ID")
    private String userId; // 빠른 조회를 위한 로그인 ID(String) 저장

    // 2. 거래 정보 (Double -> Long 통합으로 오차 방지)
    @Column(name = "TX_TYPE")
    private String txType; // DEPOSIT, WITHDRAW, TRANSFER_IN, TRANSFER_OUT, CARD_PAY

    @Column(name = "TX_AMOUNT", nullable = false)
    private Long amount; // txAmount와 통합

    @Column(name = "BALANCE_AFTER_TX")
    private Long balanceAfterTx; // 거래 후 잔액

    // 3. 거래처 및 상세 정보 (중복 필드 통합)
    @Column(name = "DESCRIPTION")
    private String description; // 적요

    @Column(name = "SOURCE_VALUE")
    private String sourceValue; // 출금 계좌번호 또는 카드번호

    @Column(name = "TARGET_ACCOUNT_NUMBER")
    private String targetAccountNumber; // 수신 계좌번호 또는 가맹점명 (targetValue 통합)

    // 4. 관리자 및 FDS 분석용 필드 (새로 추가)
    @Column(name = "MERCHANT_CAT")
    private String merchantCat; // 업종 카테고리

    @Column(name = "LOCATION")
    private String location; // 거래 위치 정보

    @Builder.Default
    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt = LocalDateTime.now(); // txTimestamp와 통합
}