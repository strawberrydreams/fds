package kdt.fds.project.repository;

import kdt.fds.project.entity.Account;
import kdt.fds.project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // 1. 계좌번호로 조회 (송금, 입금, 인출 시 필수)
    // 엔티티에 @Where(clause = "status = 'ACTIVE'")가 있다면 ACTIVE인 것만 반환됩니다.
    Optional<Account> findByAccountNumber(String accountNumber);

    // 2. 특정 유저 객체로 전체 계좌 목록 조회
    List<Account> findByUser(User user);

    // 3. 유저의 로그인 아이디(userId)를 이용해 Join 쿼리로 계좌 목록 조회
    List<Account> findByUser_UserId(String userId);
}