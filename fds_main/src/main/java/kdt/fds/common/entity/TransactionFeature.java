package kdt.fds.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "TRANSACTION_FEATURES") // schema 제거
@Getter @Setter @NoArgsConstructor
public class TransactionFeature {

    @Id
    @Column(name = "TX_ID")
    private Long txId;

    @Column(name = "OLD_BALANCE_ORG")
    private Double oldBalanceOrg;

    @Column(name = "NEW_BALANCE_ORG")
    private Double newBalanceOrg;

    @Lob // 오라클의 CLOB 타입 매핑
    @Column(name = "V_FEATURES")
    private String vFeatures;
}