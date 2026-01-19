package kdt.fds.project.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@AllArgsConstructor
public class CombinedTransactionDTO {
    private LocalDateTime transactionDate;
    private String content;
    private Long amount;
    private String type;   // "계좌" 또는 "카드"
    private String status; // "SUCCESS", "APPROVED" 등
}