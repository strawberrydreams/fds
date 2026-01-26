package kdt.fds.stats.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 이 파일은 사용자 대시보드 응답 DTO 레코드 파일이다.
 * 사용자 기본 정보와 계좌/카드/거래/탐지 요약을 담는다.
 */
public record UserDashboardResponseDTO(
        UserProfileDTO profile,
        List<AccountDTO> accounts,
        CardSummaryDTO cards,
        TransactionSummaryDTO transactions,
        DetectionSummaryDTO detections
) {
    /**
     * 사용자 프로필 DTO.
     * User 엔티티에 CREATED_AT이 없어 createdAt 필드는 제거됨.
     */
    public record UserProfileDTO(
            Long userId,
            String loginId,
            String name
    ) { }

    public record AccountDTO(
            Long accountId,
            String accountNumber,
            String status,
            BigDecimal balance,
            LocalDateTime createdAt
    ) { }

    public record CardSummaryDTO(
            long cardCount,
            BigDecimal averageCardsPerUser,
            Map<String, Long> statusCounts,
            Map<String, Long> typeCounts,
            Map<String, Long> issuerCounts
    ) { }

    public record TransactionSummaryDTO(
            long transactionCount,
            BigDecimal totalAmount,
            BigDecimal averageAmount,
            Map<String, Long> typeCounts,
            List<DateCountDTO> dailyCounts,
            List<RecentTransactionDTO> recentTransactions
    ) { }

    public record DateCountDTO(
            LocalDate date,
            long count
    ) { }

    public record RecentTransactionDTO(
            Long transactionId,
            LocalDateTime transactionAt,
            BigDecimal amount,
            String merchantCategory,
            String location,
            String targetAccountNumber,
            String description
    ) { }

    public record DetectionSummaryDTO(
            long detectedCount,
            long fraudCount,
            BigDecimal fraudRate,
            LocalDateTime latestDetectionAt
    ) { }
}
