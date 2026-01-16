package kdt.fds.project.repository;

import kdt.fds.project.entity.Account;
import kdt.fds.project.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // 1. 특정 계좌 1개의 내역 조회
    List<Transaction> findByAccountOrderByTxIdDesc(Account account);

    // 2. 특정 사용자의 특정 타입(송금/인출 등) 내역 조회
    List<Transaction> findByAccount_User_UserIdAndTxTypeOrderByCreatedAtDesc(String userId, String txType);

    // [추가] 3. 여러 계좌 리스트를 받아 모든 거래 내역을 최신순으로 조회 (마이페이지용)
    List<Transaction> findByAccountInOrderByCreatedAtDesc(List<Account> accounts);

    // 4. 송금 내역 총합 조회
    @Query("SELECT SUM(t.amount) FROM Transaction t " +
            "WHERE t.account.user.userId = :loginId " +
            "AND t.txType = 'TRANSFER_OUT'")
    Long getTotalTransferAmountByLoginId(@Param("loginId") String loginId);

    // 5. 인출 내역 총합 조회
    @Query("SELECT SUM(t.amount) FROM Transaction t " +
            "WHERE t.account.user.userId = :loginId " +
            "AND t.txType = 'WITHDRAW'")
    Long getTotalWithdrawAmountByLoginId(@Param("loginId") String loginId);
}