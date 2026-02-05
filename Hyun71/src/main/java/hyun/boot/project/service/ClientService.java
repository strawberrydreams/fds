package hyun.boot.project.service;

import hyun.boot.project.entity.Client;
import hyun.boot.project.repository.ClientRepository;
import jakarta.inject.Inject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClientService {

    @Inject
    private ClientRepository clientRepository;

    @Transactional
    public Client saveClient(Client client) {
        return clientRepository.save(client);
    }

    @Transactional(readOnly = true)
    public List<Client> findAllClients() {
        return clientRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Client findByClientNo(Long clientNo) {
        return clientRepository.findById(clientNo)
                .orElseThrow(() -> new IllegalArgumentException("해당 고객 없음"));
    }

    @Transactional(readOnly = true)
    public Client findByUserId(String userId) {
        return clientRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 고객 없음"));
    }

    @Transactional
    public void delete(Long clientNo) {
        clientRepository.deleteById(clientNo);
    }

    public boolean existsByUserId(String userId) {
        return clientRepository.existsByUserId(userId);
    }

    // ================== 수정시 자기 자신 제외 중복 검사 ==================
    public boolean existsByUserIdExceptMe(String userId, Long clientNo) {
        return clientRepository.countByUserIdAndClientNoNot(userId, clientNo) > 0;
    }
}