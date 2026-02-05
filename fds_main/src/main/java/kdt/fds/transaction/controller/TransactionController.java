package kdt.fds.transaction.controller;

import kdt.fds.transaction.entity.Transaction;
import kdt.fds.fraud.repository.FraudRepository;
import kdt.fds.transaction.repository.TransactionRepository;
import kdt.fds.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final FraudRepository fraudRepository;
    private final TransactionRepository transactionRepository;

    /**
     * [POST] 새로운 거래 생성 요청
     */
    @PostMapping
    public ResponseEntity<?> createTransaction(@RequestBody Transaction tx) {
        log.info("새로운 거래 요청 수신 - 사용자ID: {}, 출처: {}, 금액: {}",
                tx.getUserId(), tx.getSourceValue(), tx.getAmount());

        try {
            // 3단계 필터 로직이 담긴 서비스 호출
            Transaction result = transactionService.processTransfer(tx);

            return ResponseEntity.ok(Map.of(
                    "status", "PROCESSED",
                    "txId", result.getTxId(),
                    "amount", result.getAmount(),
                    "message", "거래 요청이 접수되었습니다. 고액 또는 의심 거래는 승인 대기 목록에서 확인 가능합니다."
            ));

        } catch (RuntimeException e) {
            log.warn("거래 거절 - 사유: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAIL",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("시스템 오류 발생: ", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", "서버 내부 오류가 발생했습니다."
            ));
        }
    }

    /**
     * [GET] 모든 거래 내역 조회 (대시보드/히스토리용)
     */
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getAllHistory() {
        // 통합된 리포지토리 메서드 호출
        List<Transaction> history = transactionRepository.findAllWithUserOrderByCreatedAtDesc();

        List<Map<String, Object>> result = history.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("txId", t.getTxId());
            map.put("userId", t.getUserId());

            // [해결] User 엔티티의 필드명인 'name'을 호출하도록 getName()으로 수정
            map.put("userName", (t.getUser() != null) ? t.getUser().getName() : "미등록");

            map.put("sourceValue", t.getSourceValue());
            map.put("targetValue", t.getTargetAccountNumber()); // 통합 필드명 적용
            map.put("txAmount", t.getAmount());               // 통합 필드명 적용
            map.put("txTimestamp", t.getCreatedAt());           // 통합 필드명 적용

            // 탐지 결과 정보 매핑
            int dbIsFraud = fraudRepository.findByTxId(t.getTxId())
                    .map(res -> res.getIsFraud())
                    .orElse(0);

            map.put("isFraud", dbIsFraud);
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * [DELETE] 특정 거래 기록 및 연관 데이터 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTransaction(@PathVariable("id") Long id) {
        log.info(">>> 데이터 삭제 요청 수신 - ID: {}", id);

        try {
            if (!transactionRepository.existsById(id)) {
                return ResponseEntity.badRequest().body("존재하지 않는 기록입니다.");
            }
            // 연관된 Feature, Fraud 기록까지 모두 지우는 서비스 로직 호출
            transactionService.deleteTransactionData(id);
            log.info(">>> 삭제 완료 - ID: {}", id);
            return ResponseEntity.ok("성공적으로 삭제되었습니다.");
        } catch (Exception e) {
            log.error("삭제 실패: ", e);
            return ResponseEntity.internalServerError().body("삭제 처리 중 오류 발생: " + e.getMessage());
        }
    }
}