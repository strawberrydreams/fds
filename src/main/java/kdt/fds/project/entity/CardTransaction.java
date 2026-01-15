package kdt.fds.project.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CARD_TRANSACTIONS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CardTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CARD_ID")
    private Card card; // 어떤 카드로 썼는지

    private String merchantName; // 가맹점 이름 (예: 배달의민족, 스타벅스)
    private Long amount;         // 결제 금액

    private String status;       // SUCCESS(승인), FAILED(잔액부족), BLOCKED(FDS차단)

    @Builder.Default
    private LocalDateTime approvedAt = LocalDateTime.now();
}