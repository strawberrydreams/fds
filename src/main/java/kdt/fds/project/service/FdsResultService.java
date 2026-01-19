package kdt.fds.project.service;

import kdt.fds.project.entity.FraudDetectionResult;
import kdt.fds.project.entity.Transaction;
import kdt.fds.project.entity.TransactionFeature;
import kdt.fds.project.repository.AccountRepository;
import kdt.fds.project.repository.FraudRepository;
import kdt.fds.project.repository.TransactionFeatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FdsResultService {

    private final FraudRepository fraudRepository;
    private final TransactionFeatureRepository featureRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public void saveAiResult(Transaction tx, Double prob, Double threshold, Integer isFraud, String engineMsg) {

        // 1. 엔진 태그 결정 (카드 vs 계좌이체)
        String engineTag = "CARD".equals(tx.getTxType()) ? "[Engine_A]" : "[Engine_B]";
        String finalEngineName = engineTag + " " + engineMsg;

        // 2. 결과 저장 (FraudDetectionResult 빌더 활용)
        FraudDetectionResult result = FraudDetectionResult.builder()
                .txId(tx.getTxId())
                .probability(prob)
                .thresholdValue(threshold)
                .isFraud(isFraud)
                .engine(finalEngineName)
                .build();
        fraudRepository.save(result);

        // 3. 피처 저장 (JSON 및 필드명 매핑)
        try {
            // [수정] findByAccountNum -> findByAccountNumber
            // [수정] getBalance() 리턴 타입이 Long이므로 기본값 0L 설정
            Long currentBal = accountRepository.findByAccountNumber(tx.getSourceValue())
                    .map(acc -> acc.getBalance()).orElse(0L);

            // [수정] getTxAmount() -> getAmount() (타입: Long)
            String jsonFeatures = String.format(
                    "{\"amount\": %d, \"old_bal\": %d, \"type\": \"%s\", \"loc\": \"%s\"}",
                    tx.getAmount(), currentBal, tx.getTxType(), tx.getLocation()
            );

            TransactionFeature feature = new TransactionFeature();
            feature.setTxId(tx.getTxId());
            feature.setOldBalanceOrg(currentBal.doubleValue()); // Feature 테이블이 Double을 요구할 경우 변환
            feature.setNewBalanceOrg((double) (currentBal - tx.getAmount()));
            feature.setVFeatures(jsonFeatures);

            featureRepository.save(feature);
            log.info("✅ FDS 저장 완료: {}", finalEngineName);

        } catch (Exception e) {
            log.error("❌ Feature 저장 실패: {}", e.getMessage());
            // 에러 추적을 위해 스택트레이스 출력 권장
            e.printStackTrace();
        }
    }
}