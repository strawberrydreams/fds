package kdt.fds.project.repository;

import kdt.fds.project.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    // 특정 로그인 ID를 가진 사용자의 카드 목록 조회
    List<Card> findByUser_UserId(String userId);

    // [추가] 특정 로그인 ID를 가진 사용자의 카드 중 특정 상태가 '아닌' 것만 조회
    List<Card> findByUser_UserIdAndStatusNot(String userId, String status);

    // 2. 이 메서드가 정확히 선언되어 있어야 CardService에서 빨간 줄이 사라집니다.
    Optional<Card> findByCardNumber(String cardNumber);
}