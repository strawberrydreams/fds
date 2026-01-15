package kdt.fds.project.repository;

import kdt.fds.project.entity.Card;
import kdt.fds.project.entity.CardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CardTransactionRepository extends JpaRepository<CardTransaction, Long> {

    /**
     * 특정 로그인 ID를 가진 사용자의 모든 카드 결제 내역을 최신순으로 조회
     * * @param userId 사용자의 로그인 ID (예: "testuser")
     * @return 최신순으로 정렬된 카드 결제 내역 리스트
     */
    List<CardTransaction> findByCard_User_UserIdOrderByApprovedAtDesc(String userId);

    /**
     * (추가 기능) 특정 카드의 결제 내역만 조회하고 싶을 때 사용
     */
    List<CardTransaction> findByCard_CardIdOrderByApprovedAtDesc(Long cardId);



}