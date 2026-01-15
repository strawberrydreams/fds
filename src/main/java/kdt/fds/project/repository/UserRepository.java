package kdt.fds.project.repository;

import kdt.fds.project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // 로그인 아이디(String userId)로 유저 정보 찾기
    Optional<User> findByUserId(String userId);

    // 이메일로 가입 여부 확인 등 추가 가능
    boolean existsByUserId(String userId);
}