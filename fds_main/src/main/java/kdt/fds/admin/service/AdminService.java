package kdt.fds.admin.service;

import kdt.fds.account.entity.Account;
import kdt.fds.fraud.entity.BlacklistAccount;
import kdt.fds.transaction.entity.Transaction;
import kdt.fds.account.repository.AccountRepository;
import kdt.fds.fraud.repository.BlacklistRepository;
import kdt.fds.fraud.repository.FraudRepository;
import kdt.fds.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final FraudRepository fraudRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final BlacklistRepository blacklistRepository;

    /**
     * 관리자 승인: 보류되었던 송금을 실행합니다.
     */
    @Transactional
    public String approveTransaction(Long txId) {
        // 1. 거래 정보 확인
        Transaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new RuntimeException("거래 정보를 찾을 수 없습니다. ID: " + txId));

        // 2. [수정] findByAccountNum -> findByAccountNumber
        Account sender = accountRepository.findByAccountNumber(tx.getSourceValue())
                .orElseThrow(() -> new RuntimeException("송금인 계좌가 존재하지 않습니다."));

        // [수정] getTxAmount() -> getAmount() (Long 타입 반영)
        sender.withdraw(tx.getAmount());
        accountRepository.save(sender);

        // [수정] findByAccountNum -> findByAccountNumber / getTargetValue -> getTargetAccountNumber
        accountRepository.findByAccountNumber(tx.getTargetAccountNumber()).ifPresent(receiver -> {
            // [수정] getBalance()와 setBalance()의 Long 타입 연산
            receiver.setBalance(receiver.getBalance() + tx.getAmount());
            accountRepository.save(receiver);
        });

        // 3. 탐지 결과 상태 업데이트
        fraudRepository.findByTxId(txId).ifPresent(fdsResult -> {
            fdsResult.setIsFraud(0); // 0: 정상 승인
            fdsResult.setEngine("관리자 수동 승인: 송금 완료");
            fraudRepository.save(fdsResult);
        });

        log.info("거래 승인 완료: TX_ID {}, Amount {}", txId, tx.getAmount());
        return "거래 ID [" + txId + "]가 승인되어 송금이 완료되었습니다.";
    }

    /**
     * 관리자 거절: 송금을 취소하고 수취인 계좌를 블랙리스트에 등록합니다.
     */
    @Transactional
    public String rejectTransaction(Long txId) {
        // 1. 거래 정보 조회
        Transaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new RuntimeException("기록 없음: 해당 거래 ID(" + txId + ")를 찾을 수 없습니다."));

        // 2. [수정] getTargetValue -> getTargetAccountNumber
        String scammerAccount = tx.getTargetAccountNumber();

        // [수정] 레포지토리 메서드명 확인 필요 (보통 existsByAccountNum 또는 existsByAccountNumber)
        if (!blacklistRepository.existsByAccountNum(scammerAccount)) {
            BlacklistAccount blacklist = new BlacklistAccount();
            blacklist.setAccountNum(scammerAccount);
            blacklist.setReason("관리자 수동 거절 (TX_ID: " + txId + ")");
            blacklistRepository.save(blacklist);
        }

        // 3. 탐지 결과 업데이트
        fraudRepository.findByTxId(txId).ifPresent(fdsResult -> {
            fdsResult.setIsFraud(1); // 1: 사기/차단 확정
            fdsResult.setEngine("관리자 거절 확정 및 블랙리스트 등록");
            fraudRepository.save(fdsResult);
        });

        log.info("거래 거절 및 블랙리스트 등록 완료: Account {}", scammerAccount);
        return "거절 완료: 수취인 계좌 [" + scammerAccount + "]가 블랙리스트에 등록되었습니다.";
    }

    /**
     * 블랙리스트 해제
     */
    @Transactional
    public String removeBlacklist(String accountNum) {
        return blacklistRepository.findByAccountNum(accountNum)
                .map(account -> {
                    blacklistRepository.delete(account);
                    log.info("블랙리스트 차단 해제: Account {}", accountNum);
                    return "계좌 [" + accountNum + "]의 차단이 해제되었습니다.";
                })
                .orElseThrow(() -> new RuntimeException("해당 계좌를 블랙리스트에서 찾을 수 없습니다."));
    }

    /**
     * 블랙리스트 수동 추가
     */
    @Transactional
    public String addToBlacklist(String accountNum, String reason) {
        if (blacklistRepository.existsByAccountNum(accountNum)) {
            throw new RuntimeException("이미 차단된 계좌입니다: " + accountNum);
        }

        BlacklistAccount blacklist = new BlacklistAccount();
        blacklist.setAccountNum(accountNum);
        blacklist.setReason(reason);

        blacklistRepository.save(blacklist);

        log.info("관리자 수동 차단 등록: {}", accountNum);
        return "계좌 [" + accountNum + "]가 블랙리스트에 추가되었습니다.";
    }
}