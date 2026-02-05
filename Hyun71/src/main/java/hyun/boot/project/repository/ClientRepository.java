package hyun.boot.project.repository;

import hyun.boot.project.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    boolean existsByUserId(String userId);

    Optional<Client> findByUserId(String userId);

    // ================== 수정시 자기 자신 제외 중복 검사 ==================
    int countByUserIdAndClientNoNot(String userId, Long clientNo);
}