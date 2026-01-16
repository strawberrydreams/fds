package kdt.fds.project.service;

import kdt.fds.project.entity.Account;
import kdt.fds.project.entity.Transaction;
import kdt.fds.project.entity.User;
import kdt.fds.project.repository.AccountRepository;
import kdt.fds.project.repository.TransactionRepository;
import kdt.fds.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 1. 계좌 생성 (비밀번호 암호화 적용)
     */
    @Transactional
    public void createAccount(String loginUserId, Long initialBalance, String rawPassword) {
        User user = userRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        String encodedPassword = passwordEncoder.encode(rawPassword);
        String newAccNum = String.format("%03d-%06d", (int)(Math.random() * 1000), (int)(Math.random() * 1000000));

        Account account = Account.builder()
                .user(user)
                .accountNumber(newAccNum)
                .password(encodedPassword)
                .balance(initialBalance != null ? initialBalance : 0L)
                .status("ACTIVE")
                .build();

        accountRepository.save(account);
        log.info("새 계좌 생성 완료: {}, 소유자: {}", newAccNum, loginUserId);
    }

    /**
     * 2. 계좌 목록 조회
     */
    @Transactional(readOnly = true)
    public List<Account> getAccountsByLoginId(String loginUserId) {
        User user = userRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보가 없습니다."));
        return accountRepository.findByUser(user);
    }

    /**
     * [추가] 3. 마이페이지용 통합 거래 내역 조회 (입금, 이체, 인출 포함)
     */
    @Transactional(readOnly = true)
    public List<Transaction> getRecentTransactionsByLoginId(String loginId) {
        List<Account> myAccounts = getAccountsByLoginId(loginId);
        if (myAccounts.isEmpty()) {
            return Collections.emptyList();
        }
        // 레포지토리에 추가한 In 구문을 사용하여 통합 조회
        return transactionRepository.findByAccountInOrderByCreatedAtDesc(myAccounts);
    }

    /**
     * 4. 입금 처리
     */
    @Transactional
    public void deposit(String accountNumber, Long amount, String password) {
        checkPassword(accountNumber, password);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없습니다."));

        account.setBalance(account.getBalance() + amount);
        saveTransaction(account, "DEPOSIT", amount, account.getBalance(), "직접 입금", null);
    }

    /**
     * 5. 송금 처리
     */
    @Transactional
    public void transfer(String fromAccNum, String toAccNum, Long amount, String password) {
        checkPassword(fromAccNum, password);
        Account fromAccount = accountRepository.findByAccountNumber(fromAccNum)
                .orElseThrow(() -> new IllegalArgumentException("출금 계좌를 찾을 수 없습니다."));
        Account toAccount = accountRepository.findByAccountNumber(toAccNum)
                .orElseThrow(() -> new IllegalArgumentException("입금 계좌를 찾을 수 없습니다."));

        if (fromAccNum.equals(toAccNum)) throw new IllegalArgumentException("동일한 계좌로 송금할 수 없습니다.");
        if (fromAccount.getBalance() < amount) throw new IllegalStateException("잔액이 부족합니다.");

        fromAccount.setBalance(fromAccount.getBalance() - amount);
        toAccount.setBalance(toAccount.getBalance() + amount);

        saveTransaction(fromAccount, "TRANSFER_OUT", amount, fromAccount.getBalance(), "송금 완료", toAccNum);
        saveTransaction(toAccount, "TRANSFER_IN", amount, toAccount.getBalance(), "입금 완료", fromAccNum);
    }

    /**
     * 6. 인출 처리
     */
    @Transactional
    public void withdraw(String accountNumber, Long amount, String password) {
        checkPassword(accountNumber, password);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없습니다."));

        if (account.getBalance() < amount) throw new IllegalStateException("잔액이 부족합니다.");

        account.setBalance(account.getBalance() - amount);
        saveTransaction(account, "WITHDRAW", amount, account.getBalance(), "ATM 현금 인출", null);
    }

    // 내역 조회 보조 메서드들
    public List<Transaction> getRecentTransferHistory(String loginId) {
        return transactionRepository.findByAccount_User_UserIdAndTxTypeOrderByCreatedAtDesc(loginId, "TRANSFER_OUT");
    }

    public List<Transaction> getRecentWithdrawHistory(String loginId) {
        return transactionRepository.findByAccount_User_UserIdAndTxTypeOrderByCreatedAtDesc(loginId, "WITHDRAW");
    }

    @Transactional(readOnly = true)
    public Long getTotalTransferAmount(String loginId) {
        Long total = transactionRepository.getTotalTransferAmountByLoginId(loginId);
        return (total != null) ? total : 0L;
    }

    @Transactional(readOnly = true)
    public Long getTotalWithdrawAmount(String loginId) {
        Long total = transactionRepository.getTotalWithdrawAmountByLoginId(loginId);
        return (total != null) ? total : 0L;
    }

    /**
     * 7. 계좌 해지 (Soft Delete)
     */
    @Transactional
    public void deleteAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없습니다."));
        if (account.getBalance() > 0) throw new IllegalStateException("잔액이 남아있습니다.");
        account.setStatus("DELETED");
    }

    private void checkPassword(String accountNumber, String inputPassword) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없습니다."));
        if (!passwordEncoder.matches(inputPassword, account.getPassword())) {
            throw new IllegalArgumentException("계좌 비밀번호가 일치하지 않습니다.");
        }
    }

    private void saveTransaction(Account account, String type, Long amount, Long balanceAfter, String desc, String targetAcc) {
        Transaction tx = Transaction.builder()
                .account(account)
                .txType(type)
                .amount(amount)
                .balanceAfterTx(balanceAfter)
                .description(desc)
                .targetAccountNumber(targetAcc)
                .createdAt(LocalDateTime.now())
                .build();
        transactionRepository.save(tx);
    }
}