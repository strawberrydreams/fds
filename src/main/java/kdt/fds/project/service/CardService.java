package kdt.fds.project.service;

import kdt.fds.project.entity.Account;
import kdt.fds.project.entity.Card;
import kdt.fds.project.entity.CardTransaction;
import kdt.fds.project.entity.User;
import kdt.fds.project.repository.AccountRepository;
import kdt.fds.project.repository.CardRepository;
import kdt.fds.project.repository.CardTransactionRepository;
import kdt.fds.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 로그 기록을 위해 추가
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Slf4j // 로그 사용을 위해 추가
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 기본적으로 읽기 전용으로 설정하여 성능 최적화
public class CardService {

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CardTransactionRepository cardTransactionRepository;

    /**
     * 카드 발급 로직
     */
    @Transactional // DB 쓰기 작업이므로 명시적 설정
    public void issueCard(String loginId, String accountNumber, String accountPassword, String cardType) {
        User user = userRepository.findByUserId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계좌번호입니다."));

        if (!passwordEncoder.matches(accountPassword, account.getPassword())) {
            throw new IllegalArgumentException("계좌 비밀번호가 일치하지 않습니다.");
        }

        Card card = Card.builder()
                .user(user)
                .account(account)
                .cardNumber(generateRandomCardNumber())
                .cardType(cardType)
                .status("ACTIVE")
                .build();

        cardRepository.save(card);
    }

    /**
     * 사용자의 카드 목록 조회 (해지된 카드 제외)
     */
    public List<Card> getCardsByLoginId(String userId) {
        return cardRepository.findByUser_UserIdAndStatusNot(userId, "TERMINATED");
    }

    /**
     * 카드 번호 생성 (4-4-4-4 포맷)
     */
    private String generateRandomCardNumber() {
        Random r = new Random();
        return String.format("%04d-%04d-%04d-%04d",
                r.nextInt(10000), r.nextInt(10000), r.nextInt(10000), r.nextInt(10000));
    }

    /**
     * 카드 상태 단순 변경 (분실신고 등)
     */
    @Transactional
    public void updateCardStatus(Long cardId, String newStatus) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카드입니다."));
        card.setStatus(newStatus);
    }

    /**
     * 인증을 통한 카드 상태 변경 (해지, 분실해제 등)
     */
    @Transactional
    public void updateCardStatusWithAuth(Long cardId, String status, String password) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카드입니다."));

        if (!passwordEncoder.matches(password, card.getAccount().getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        card.setStatus(status);
    }

    public Card getCardById(Long cardId) {
        return cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카드입니다. ID: " + cardId));
    }

    /**
     * 카드 결제 내역 전체 조회
     */
    public List<CardTransaction> getCardTransactionsByLoginId(String userId) {
        return cardTransactionRepository.findByCard_User_UserIdOrderByApprovedAtDesc(userId);
    }

    /**
     * 실제 카드 결제 처리 로직
     */
    @Transactional // 잔액 차감 + 내역 저장이 한 묶음으로 처리되어야 함
    public void processPayment(String cardNumber, Long amount, String merchantName) {
        // 1. 카드 및 연결 계좌 조회
        Card card = cardRepository.findByCardNumber(cardNumber)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카드 번호입니다."));

        // 2. 카드 활성 상태 체크
        if (!"ACTIVE".equals(card.getStatus())) {
            throw new IllegalStateException("사용할 수 없는 카드입니다. (현재 상태: " + card.getStatus() + ")");
        }

        // 3. 계좌 및 잔액 체크
        Account account = card.getAccount();
        if (account == null) {
            throw new IllegalStateException("연결된 계좌 정보가 없습니다.");
        }

        if (account.getBalance() < amount) {
            // 실패 내역을 저장하고 싶다면 여기서 saveTransaction 호출 가능
            throw new IllegalStateException("계좌 잔액이 부족합니다.");
        }

        // 4. 잔액 차감
        account.setBalance(account.getBalance() - amount);
        // JPA Dirty Checking으로 변경 감지되어 자동 업데이트됨

        // 5. 결제 내역 저장
        CardTransaction tx = CardTransaction.builder()
                .card(card)
                .merchantName(merchantName)
                .amount(amount)
                .status("SUCCESS")
                .approvedAt(LocalDateTime.now())
                .build();

        cardTransactionRepository.save(tx);

        log.info("결제 승인 완료 - 가맹점: {}, 금액: {}, 카드: {}", merchantName, amount, cardNumber);
    }
}