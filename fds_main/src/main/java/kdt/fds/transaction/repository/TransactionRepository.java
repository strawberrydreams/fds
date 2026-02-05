package kdt.fds.transaction.repository;

import kdt.fds.account.entity.Account;
import kdt.fds.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // ==========================================
    // 1. 사용자 화면용 (마이페이지 및 계좌 상세)
    // ==========================================

    /**
     * 특정 계좌의 모든 내역 조회 (최신순)
     */
    List<Transaction> findByAccountOrderByTxIdDesc(Account account);

    /**
     * 특정 사용자의 특정 타입(송금/인출 등) 내역 조회
     */
    List<Transaction> findByAccount_User_UserIdAndTxTypeOrderByCreatedAtDesc(String userId, String txType);

    /**
     * 여러 계좌 리스트를 받아 모든 거래 내역을 최신순으로 조회 (마이페이지용)
     */
    List<Transaction> findByAccountInOrderByCreatedAtDesc(List<Account> accounts);

    /**
     * 특정 계좌번호(SourceValue)의 최근 거래 10건
     */
    List<Transaction> findTop10BySourceValueOrderByCreatedAtDesc(String sourceValue);

    /**
     * [추가] 특정 계좌번호(SourceValue)로 조회하여 가장 최근 거래 1건을 가져옵니다. (신고 연동용)
     */
    Optional<Transaction> findFirstBySourceValueOrderByCreatedAtDesc(String sourceValue);

    // ==========================================
    // 2. 관리자 및 FDS 모니터링용
    // ==========================================

    /**
     * 모든 거래 내역 조회 (성능 최적화를 위해 User 정보 JOIN FETCH)
     */
    @Query("SELECT t FROM Transaction t JOIN FETCH t.user ORDER BY t.createdAt DESC")
    List<Transaction> findAllWithUserOrderByCreatedAtDesc();

    /**
     * 고액 거래 필터링 (기준 금액 이상)
     */
    @Query("SELECT t FROM Transaction t JOIN FETCH t.user WHERE t.amount >= :minAmount ORDER BY t.createdAt DESC")
    List<Transaction> findHighAmountTransactions(@Param("minAmount") Long minAmount);

    /**
     * 특정 수취인(가맹점 또는 수신계좌) 거래 조회
     */
    List<Transaction> findByTargetAccountNumber(String targetAccountNumber);

    // ==========================================
    // 3. 통계 및 집계 쿼리
    // ==========================================

    /**
     * 사용자의 총 송금액 합계
     */
    @Query("SELECT SUM(t.amount) FROM Transaction t " +
            "WHERE t.account.user.userId = :loginId " +
            "AND t.txType = 'TRANSFER_OUT'")
    Long getTotalTransferAmountByLoginId(@Param("loginId") String loginId);

    /**
     * 사용자의 총 인출액 합계
     */
    @Query("SELECT SUM(t.amount) FROM Transaction t " +
            "WHERE t.account.user.userId = :loginId " +
            "AND t.txType = 'WITHDRAW'")
    Long getTotalWithdrawAmountByLoginId(@Param("loginId") String loginId);

    /**
     * 전체 거래 최신순 조회
     */
    List<Transaction> findAllByOrderByCreatedAtDesc();
}