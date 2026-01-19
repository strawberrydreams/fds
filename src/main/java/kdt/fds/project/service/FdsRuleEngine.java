package kdt.fds.project.service;

import kdt.fds.project.entity.Transaction;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class FdsRuleEngine {

    /**
     * 규칙 기반 탐지 (AI 호출 전 실행)
     * @return 사기 의심 사유 (정상이면 null)
     */
    public String evaluateRules(Transaction tx) {

        // 1. [수정] 고액 거래 규칙 (getTxAmount -> getAmount)
        // 5,000만원 이상 (Long 타입이므로 숫자 뒤에 L을 붙여주는 것이 좋습니다)
        if (tx.getAmount() >= 50000000L) {
            return "RULE: HIGH_AMOUNT_LIMIT";
        }

        // 2. [수정] 심야 고액 거래 규칙 (00시 ~ 05시 사이 100만원 이상)
        // 필드명 수정: getTxAmount -> getAmount
        int hour = LocalDateTime.now().getHour();
        if ((hour >= 0 && hour <= 5) && tx.getAmount() >= 1000000L) {
            return "RULE: NIGHT_SUSPICIOUS_TRANSFER";
        }

        // 3. (추가 가능) 특정 위험 지역 거래 등...

        return null; // 통과
    }
}