package kdt.fds.project.repository;

import kdt.fds.project.entity.BlacklistAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BlacklistRepository extends JpaRepository<BlacklistAccount, Long> {

    // 특정 계좌번호가 블랙리스트에 이미 등록되어 있는지 확인하는 메서드
    Optional<BlacklistAccount> findByAccountNum(String accountNum);
    boolean existsByAccountNum(String accountNum);
}