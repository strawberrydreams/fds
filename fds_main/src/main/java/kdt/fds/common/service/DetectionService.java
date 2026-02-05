package kdt.fds.common.service;

import kdt.fds.transaction.entity.Transaction;
import kdt.fds.account.repository.AccountRepository;
import kdt.fds.common.repository.FdsConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionService {

    private final FdsResultService resultService;
    private final FdsRuleEngine ruleEngine;
    private final FdsConfigRepository configRepository;
    private final AccountRepository accountRepository;

    // RestTemplate 주입 (Bean 설정이 없다면 생성자에서 초기화도 가능)
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String FLASK_URL = "http://localhost:5001/api/predict";

    private static final String KEY_THRESHOLD = "THRESHOLD";
    private static final String KEY_AUTO_LIMIT = "AUTO_LIMIT";

    public int detectAndSave(Transaction tx) {
        // [수정] getTxAmount() -> getAmount()
        log.info("🛡️ 탐지 프로세스 시작 - TX_ID: {}, 금액: {}", tx.getTxId(), tx.getAmount());

        // =================================================================
        // [핵심 1] 설정값 조회
        // =================================================================
        double threshold = Double.parseDouble(configRepository.findById(KEY_THRESHOLD)
                .map(c -> c.getConfigValue()).orElse("0.7"));

        long autoLimit = Long.parseLong(configRepository.findById(KEY_AUTO_LIMIT)
                .map(c -> c.getConfigValue()).orElse("100000"));

        // =================================================================
        // [관문 1] Rule 엔진 체크
        // =================================================================
        String ruleViolation = ruleEngine.evaluateRules(tx);
        if (ruleViolation != null) {
            String reason = mapReasonToKorean(ruleViolation);
            resultService.saveAiResult(tx, 1.0, threshold, 1, "[Rule] " + reason);
            log.warn("⛔ 룰 기반 즉시 차단: {}", reason);
            return 1;
        }

        // =================================================================
        // [관문 2 & 3] AI 판정 및 금액 한도 체크
        // =================================================================
        try {
            // [수정] findByAccountNum -> findByAccountNumber
            // [수정] getBalance() 타입 반영 (Long)
            long currentBalance = accountRepository.findByAccountNumber(tx.getSourceValue())
                    .map(acc -> acc.getBalance()).orElse(0L);

            // AI 서버 요청 맵 구성 (필드명 수정 반영)
            Map<String, Object> request = Map.of(
                    "user_id", tx.getUserId(),
                    "amount", tx.getAmount(),       // getTxAmount -> getAmount
                    "location", tx.getLocation(),
                    "old_bal", currentBalance,
                    "tx_type", tx.getTxType()
            );

            // AI 서버 호출
            Map<String, Object> response = restTemplate.postForObject(FLASK_URL, request, Map.class);

            if (response != null && "success".equals(response.get("status"))) {
                Double probability = Double.valueOf(response.get("probability").toString());
                String flaskEngine = response.get("engine").toString();

                // --- [판단 로직] ---
                boolean isAiSafe = probability < threshold;
                boolean isAmountSafe = tx.getAmount() <= autoLimit; // getTxAmount -> getAmount

                int finalDecision;
                String decisionReason;

                if (isAiSafe && isAmountSafe) {
                    finalDecision = 0;
                    decisionReason = flaskEngine + " (정상 승인)";
                } else {
                    finalDecision = 1;
                    if (!isAiSafe) {
                        decisionReason = flaskEngine + " (위험도 높음)";
                    } else {
                        decisionReason = flaskEngine + " (AI 안전하나 금액 한도 초과)";
                    }
                }

                resultService.saveAiResult(tx, probability, threshold, finalDecision, decisionReason);
                return finalDecision;
            }

        } catch (Exception e) {
            log.error("AI 서버 에러: {}", e.getMessage());
            resultService.saveAiResult(tx, 0.0, threshold, 1, "[System] AI 서버 오류");
            return 1;
        }

        return 1;
    }

    /**
     * 필터/블랙리스트 차단 시 호출
     */
    public void saveFilterResult(Transaction tx, String reason) {
        // 일관성을 위해 기본 임계치 0.7 기록 (필요시 조회 로직 추가 가능)
        resultService.saveAiResult(tx, 1.0, 0.7, 1, "[Blacklist] " + reason);
    }

    private String mapReasonToKorean(String violation) {
        if (violation.contains("HIGH_AMOUNT")) return "고액 거래 (규칙 위반)";
        if (violation.contains("NIGHT")) return "심야 의심 거래";
        return "보안 정책 위반: " + violation;
    }
}