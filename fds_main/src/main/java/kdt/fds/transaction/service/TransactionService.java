package kdt.fds.transaction.service;

import kdt.fds.account.entity.Account;
import kdt.fds.account.repository.AccountRepository;
import kdt.fds.common.repository.FdsConfigRepository;
import kdt.fds.common.repository.TransactionFeatureRepository;
import kdt.fds.common.service.DetectionService;
import kdt.fds.fraud.repository.BlacklistRepository;
import kdt.fds.fraud.repository.FraudRepository;
import kdt.fds.transaction.entity.Transaction;
import kdt.fds.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final DetectionService detectionService;
    private final BlacklistRepository blacklistRepository;
    private final FdsConfigRepository configRepository;

    private final TransactionFeatureRepository featureRepository;
    private final FraudRepository fraudRepository;

    @Transactional
    public Transaction processTransfer(Transaction txRequest) {
        // 1. 기본 정보 설정 (필드명 수정: txTimestamp -> createdAt)
        txRequest.setCreatedAt(LocalDateTime.now());

        // [수정] 통합된 AccountRepository 메서드 사용: findByAccountNumber
        Account senderAccount = accountRepository.findByAccountNumber(txRequest.getSourceValue())
                .orElseThrow(() -> new RuntimeException("송금 계좌를 찾을 수 없습니다."));

        // [수정] sourceId 설정 (Entity 필드명 확인)
        // txRequest.setSourceId(senderAccount.getAccountId());

        // 거래 이력 선 저장
        Transaction savedTx = transactionRepository.save(txRequest);

        // -------------------------------------------------------------------------
        // [자동 승인 필터링]
        // -------------------------------------------------------------------------

        // 조건 1: 블랙리스트 (필드명 수정: targetValue -> targetAccountNumber)
        if (blacklistRepository.existsByAccountNum(txRequest.getTargetAccountNumber())) {
            log.warn("🚫 [차단] 블랙리스트: {}", txRequest.getTargetAccountNumber());
            detectionService.saveFilterResult(savedTx, "블랙리스트 계좌 탐지");
            return savedTx;
        }

        // 조건 2: 한도 초과 (필드명 수정: txAmount -> amount)
        double autoApproveLimit = configRepository.findById("AUTO_APPROVE_AMOUNT")
                .map(c -> Double.parseDouble(c.getConfigValue())).orElse(100000.0);

        if (txRequest.getAmount() > autoApproveLimit) {
            log.warn("⚠️ [격리] 한도 초과: {}원", txRequest.getAmount());
            detectionService.saveFilterResult(savedTx, "자동 승인 한도 초과");
            return savedTx;
        }

        // 조건 3: AI 판정
        int fraudStatus = detectionService.detectAndSave(savedTx);

        if (fraudStatus == 1) {
            log.warn("⚠️ [격리] AI 판정 이상 거래");
            return savedTx;
        }

        // -------------------------------------------------------------------------
        // [최종 승인] 잔액 이동
        // -------------------------------------------------------------------------
        return executeTransfer(savedTx, senderAccount);
    }

    /**
     * 실제 잔액 이동 처리 (AdminController에서도 호출함)
     */
    @Transactional
    public Transaction executeTransfer(Transaction tx, Account sender) {
        // [수정] 필드명 수정: txAmount -> amount
        sender.withdraw(tx.getAmount());
        accountRepository.save(sender);

        // [수정] 수취인 계좌 조회 및 입금 (targetAccountNumber 사용)
        accountRepository.findByAccountNumber(tx.getTargetAccountNumber()).ifPresent(receiver -> {
            receiver.setBalance(receiver.getBalance() + tx.getAmount());
            accountRepository.save(receiver);
        });

        log.info("💸 이체 완료 TX_ID: {}", tx.getTxId());
        return tx;
    }

    /**
     * 거래 관련 모든 데이터 삭제 (격리 해제나 삭제 시 사용)
     */
    @Transactional
    public void deleteTransactionData(Long txId) {
        log.info("삭제 프로세스 시작 - TX_ID: {}", txId);

        // 1. [자식] 피처 데이터 삭제
        if (featureRepository.existsById(txId)) {
            featureRepository.deleteById(txId);
        }

        // 2. [자식] 탐지 결과 삭제
        fraudRepository.deleteByTxId(txId);

        // 3. [부모] 거래 원장 삭제
        transactionRepository.deleteById(txId);

        log.info("삭제 완료 - 모든 연관 데이터 제거됨");
    }
}
