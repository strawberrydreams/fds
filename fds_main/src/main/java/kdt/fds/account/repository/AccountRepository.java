package kdt.fds.account.repository;

import kdt.fds.account.entity.Account;
import kdt.fds.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByUser(User user);

    List<Account> findByUser_UserId(String userId);

    // [수정] 필드명이 name이므로 UserName -> Name으로 변경
    boolean existsByUser_Name(String name);
}