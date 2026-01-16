package kdt.fds.project.service;

import kdt.fds.project.entity.Account;
import kdt.fds.project.entity.Transaction;
import kdt.fds.project.repository.AccountRepository;
import kdt.fds.project.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public void transfer(Long fromAccountId, String toAccountNumber, Long amount) {
        // 1. 보내는 계좌 조회
        Account fromAccount = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("출금 계좌 오류"));

        // 2. 받는 계좌 조회 (계좌번호로 조회)
        Account toAccount = accountRepository.findByAccountNumber(toAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException("상대방 계좌번호를 찾을 수 없습니다."));

        // 3. 잔액 확인 및 차감
        if (fromAccount.getBalance() < amount) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }

        fromAccount.setBalance(fromAccount.getBalance() - amount);
        toAccount.setBalance(toAccount.getBalance() + amount);

        // 4. 보내는 사람 거래 내역 기록
        Transaction sentTx = Transaction.builder()
                .account(fromAccount)
                .txType("TRANSFER_SENT")
                .amount(amount)
                .targetAccountNumber(toAccountNumber)
                .balanceAfterTx(fromAccount.getBalance())
                .build();
        transactionRepository.save(sentTx);

        // 5. 받는 사람 거래 내역 기록 (선택 사항)
        Transaction recvTx = Transaction.builder()
                .account(toAccount)
                .txType("TRANSFER_RECV")
                .amount(amount)
                .targetAccountNumber(fromAccount.getAccountNumber())
                .balanceAfterTx(toAccount.getBalance())
                .build();
        transactionRepository.save(recvTx);
    }
}