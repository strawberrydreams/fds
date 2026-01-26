package kdt.fds.stats.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 이 파일은 사용자 요약 응답 DTO 레코드 파일이다.
 * 실시간 개인 요약 KPI를 반환한다.
 */
public record UserSummaryResponseDTO(
        String range,
        long transactionCount,
        BigDecimal totalAmount,
        BigDecimal averageAmount,
        long detectedCount,
        BigDecimal detectedRate,
        long fraudCount,
        BigDecimal fraudRate,
        Double averageFraudProbability,
        Double medianFraudProbability,
        LocalDateTime latestTransactionAt,
        LocalDateTime latestDetectionAt
) { }
