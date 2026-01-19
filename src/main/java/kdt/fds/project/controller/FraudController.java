package kdt.fds.project.controller;

import kdt.fds.project.dto.FraudDetailDTO;
import kdt.fds.project.entity.Account;
import kdt.fds.project.entity.FraudDetectionResult;
import kdt.fds.project.entity.Transaction;
import kdt.fds.project.repository.AccountRepository;
import kdt.fds.project.repository.FraudRepository;
import kdt.fds.project.repository.TransactionRepository;
import kdt.fds.project.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
public class FraudController {

    private final FraudRepository fraudRepository;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    /**
     * [READ] 전체 탐지 이력 조회
     */
    @GetMapping("/all")
    public ResponseEntity<List<FraudDetailDTO>> getAllHistory() {
        log.info("금융사기 탐지 이력 조회 요청 수신");
        List<FraudDetailDTO> history = fraudRepository.findAllWithDetails();
        return ResponseEntity.ok(history);
    }

    /**
     * [UPDATE] 사기 여부 상태 변경 (정상 판정 시 송금 실행)
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<String> updateStatus(
            @PathVariable("id") Long id,
            @RequestParam("isFraud") Integer isFraud) {

        log.info("상태 변경 요청 - ID: {}, Status: {}", id, isFraud);

        // 1. 탐지 결과 조회 (txId 기준 혹은 PK 기준)
        FraudDetectionResult result = fraudRepository.findByTxId(id)
                .orElseGet(() -> fraudRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("기록을 찾을 수 없습니다.")));

        result.setIsFraud(isFraud);
        fraudRepository.save(result);

        // 2. 정상(0)으로 판정 변경 시, 묶여있던 송금 실행
        if (isFraud == 0) {
            Transaction tx = transactionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("거래 정보가 없습니다. ID: " + id));

            // [수정] findByAccountNum -> findByAccountNumber (통합된 메서드명)
            Account sender = accountRepository.findByAccountNumber(tx.getSourceValue())
                    .orElseThrow(() -> new RuntimeException("송금 계좌를 찾을 수 없습니다."));

            // 서비스 로직 호출 (실제 잔액 이동)
            transactionService.executeTransfer(tx, sender);
            log.info("✅ 관리자 승인에 따른 송금 실행 완료: TX_ID {}", id);
        }

        return ResponseEntity.ok("SUCCESS");
    }

    /**
     * [DELETE] 기록 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteHistory(@PathVariable("id") Long id) {
        log.info("기록 삭제 요청 - ID: {}", id);

        // 단순히 탐지 결과만 삭제할지, 연관된 거래 데이터를 모두 삭제할지에 따라
        // transactionService.deleteTransactionData(id) 호출을 고려할 수 있습니다.
        fraudRepository.deleteById(id);
        return ResponseEntity.ok("SUCCESS");
    }
}