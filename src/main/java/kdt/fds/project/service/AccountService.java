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
     * 1. 계좌 생성 (소프트 딜리트 구조 적용)
     */
    @Transactional
    public void createAccount(String loginUserId, Long initialBalance, String rawPassword) {
        User user = userRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        // 1. 비밀번호 암호화 (BCrypt 사용)
        // rawPassword(예: "1234") -> encodedPassword(예: "$2a$10$...")
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // 2. 계좌번호 랜덤 생성
        String newAccNum = String.format("%03d-%06d",
                (int)(Math.random() * 1000), (int)(Math.random() * 1000000));

        Account account = Account.builder()
                .user(user)
                .accountNumber(newAccNum)
                .password(encodedPassword) // 암호화된 비밀번호를 저장!
                .balance(initialBalance != null ? initialBalance : 0L)
                .status("ACTIVE")
                .build();

        accountRepository.save(account);
        log.info("새 계좌 생성 완료 (비밀번호 암호화 적용): {}, 소유자: {}", newAccNum, loginUserId);
    }

    /**
     * 2. 계좌 목록 조회
     * 엔티티의 @Where(clause = "status = 'ACTIVE'") 덕분에 ACTIVE인 것만 조회됨
     */
    @Transactional(readOnly = true)
    public List<Account> getAccountsByLoginId(String loginUserId) {
        User user = userRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보가 없습니다."));
        return accountRepository.findByUser(user);
    }

    /**
     * 3. 특정 사용자의 송금 기록 조회
     */
    @Transactional(readOnly = true)
    public List<Transaction> getTransferHistory(String loginUserId) {
        return transactionRepository.findByAccount_User_UserIdAndTxTypeOrderByCreatedAtDesc(loginUserId, "TRANSFER_OUT");
    }

    /**
     * 4. 입금 처리 (입금 시 비밀번호 확인은 정책에 따라 선택 가능)
     */
    @Transactional
    public void deposit(String accountNumber, Long amount, String password) {
        // 비밀번호 검증이 필요 없다면 제거 가능
        checkPassword(accountNumber, password);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없거나 해지된 계좌입니다."));

        account.setBalance(account.getBalance() + amount);

        saveTransaction(account, "DEPOSIT", amount, account.getBalance(), "직접 입금", null);
        log.info("입금 완료: 계좌={}, 금액={}", accountNumber, amount);
    }

    /**
     * 5. 송금 처리 (출금 계좌 비밀번호 검증)
     */
    @Transactional
    public void transfer(String fromAccNum, String toAccNum, Long amount, String password) {
        // 1. 출금 계좌 비밀번호 확인 (여기서 계좌 존재 여부 및 ACTIVE 여부 자동 체크)
        checkPassword(fromAccNum, password);

        Account fromAccount = accountRepository.findByAccountNumber(fromAccNum)
                .orElseThrow(() -> new IllegalArgumentException("출금 계좌를 찾을 수 없습니다."));
        Account toAccount = accountRepository.findByAccountNumber(toAccNum)
                .orElseThrow(() -> new IllegalArgumentException("입금 계좌를 찾을 수 없습니다."));

        // 2. 본인 이체 방지 (선택 사항)
        if (fromAccNum.equals(toAccNum)) {
            throw new IllegalArgumentException("동일한 계좌로 송금할 수 없습니다.");
        }

        // 3. 잔액 검증
        if (fromAccount.getBalance() < amount) {
            throw new IllegalStateException("잔액이 부족합니다. 현재 잔액: " + fromAccount.getBalance() + "원");
        }

        // 4. 이체 실행
        fromAccount.setBalance(fromAccount.getBalance() - amount);
        toAccount.setBalance(toAccount.getBalance() + amount);

        // 5. 내역 기록
        saveTransaction(fromAccount, "TRANSFER_OUT", amount, fromAccount.getBalance(), "송금 완료", toAccNum);
        saveTransaction(toAccount, "TRANSFER_IN", amount, toAccount.getBalance(), "입금 완료", fromAccNum);
    }
    public List<Transaction> getRecentTransferHistory(String loginId) {
        // 유저의 계좌 목록 조회
        List<Account> accounts = getAccountsByLoginId(loginId);

        // 2. 해당 계좌들의 거래 내역 중 'TRANSFER_OUT' 타입만 최신순으로 조회
        // TransactionRepository에 메서드 추가 필요:
        // findByAccountInAndTxTypeOrderByCreatedAtDesc(accounts, "TRANSFER_OUT")
        return transactionRepository.findByAccount_User_UserIdAndTxTypeOrderByCreatedAtDesc(loginId, "TRANSFER_OUT");
    }

    /**
     * 6. 인출 처리
     */
    @Transactional
    public void withdraw(String accountNumber, Long amount, String password) {
        checkPassword(accountNumber, password);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없거나 해지된 계좌입니다."));

        if (account.getBalance() < amount) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }

        account.setBalance(account.getBalance() - amount);
        saveTransaction(account, "WITHDRAW", amount, account.getBalance(), "ATM 현금 인출", null);
    }

    //인출내역조회
    public List<Transaction> getRecentWithdrawHistory(String loginId) {
        // 1. 유저의 계좌 목록 조회
        List<Account> accounts = getAccountsByLoginId(loginId);

        // 2. 해당 계좌들의 거래 내역 중 'WITHDRAW' 타입만 최신순으로 조회
        // 기존에 만드신 레포지토리 메서드를 활용 (타입만 WITHDRAW로 전달)
        return transactionRepository.findByAccount_User_UserIdAndTxTypeOrderByCreatedAtDesc(loginId, "WITHDRAW");
    }

    /**
     * . 특정 사용자의 총 누적 송금액 조회
     */
    @Transactional(readOnly = true)
    public Long getTotalTransferAmount(String loginId) {
        // 레포지토리에서 합계 조회
        // 결과가 null이면 송금 내역이 없는 것이므로 0L 반환
        Long total = transactionRepository.getTotalTransferAmountByLoginId(loginId);
        return (total != null) ? total : 0L;
    }

    // 인출총금액
    @Transactional(readOnly = true)
    public Long getTotalWithdrawAmount(String loginId) {
        // 레포지토리에서 합계 조회
        // 결과가 null이면 송금 내역이 없는 것이므로 0L 반환
        Long total = transactionRepository.getTotalWithdrawAmountByLoginId(loginId);
        return (total != null) ? total : 0L;
    }


    /**
     * 7. 계좌 해지 (Soft Delete)
     */
    @Transactional
    public void deleteAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 이미 해지된 계좌입니다."));

        // 잔액이 남아있으면 해지 불가
        if (account.getBalance() > 0) {
            throw new IllegalStateException("잔액이 남아있는 계좌는 해지할 수 없습니다. 먼저 잔액을 인출하세요.");
        }

        // Soft Delete: 상태값 변경
        account.setStatus("DELETED");
        log.info("계좌 해지 완료: ID={}, 계좌번호={}", accountId, account.getAccountNumber());
    }

    /**
     * [내부 로직] 계좌 비밀번호 및 상태 확인
     */

    private void checkPassword(String accountNumber, String inputPassword) {
        // findByAccountNumber는 @Where 적용으로 ACTIVE인 것만 가져옴
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("사용 가능한 계좌를 찾을 수 없습니다."));

        // AS-IS: account.getPassword().equals(inputPassword)
        // TO-BE: passwordEncoder.matches(평문, 암호문) 사용
        if (account.getPassword() == null || !passwordEncoder.matches(inputPassword, account.getPassword())) {
            throw new IllegalArgumentException("계좌 비밀번호가 일치하지 않습니다.");
        }
    }

    /**
     * 공통 로직: 거래 내역 저장
     */
    private void saveTransaction(Account account, String type, Long amount, Long balanceAfter, String desc, String targetAcc) {
        Transaction tx = Transaction.builder()
                .account(account)
                .txType(type)
                .amount(amount)
                .balanceAfterTx(balanceAfter)
                .description(desc)
                .targetAccountNumber(targetAcc)
                .build();
        transactionRepository.save(tx);
    }



}