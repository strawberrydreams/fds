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

    // 2. 서비스에서 빨간 줄 뜬 바로 그 메서드!
    // Transaction -> Account -> User -> userId 순서로 찾아 들어갑니다.
    List<Transaction> findByAccount_User_UserIdAndTxTypeOrderByCreatedAtDesc(String userId, String txType);

    //3 송금내역 총합
    // TransactionRepository.java

    @Query("SELECT SUM(t.amount) FROM Transaction t " +
            "WHERE t.account.user.userId = :loginId " + // 1. t.account.user.필드명 확인
            "AND t.txType = 'TRANSFER_OUT'")
    Long getTotalTransferAmountByLoginId(@Param("loginId") String loginId); // 2. @Param 이름 확인

    // 인출내역 통합
    // TransactionRepository.java

    @Query("SELECT SUM(t.amount) FROM Transaction t " +
            "WHERE t.account.user.userId = :loginId " +
            "AND t.txType = 'WITHDRAW'")
    Long getTotalWithdrawAmountByLoginId(@Param("loginId") String loginId);

}