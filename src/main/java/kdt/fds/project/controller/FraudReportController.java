package kdt.fds.project.controller;

import kdt.fds.project.entity.FraudDetectionResult;
import kdt.fds.project.entity.FraudReport;
import kdt.fds.project.entity.Transaction;
import kdt.fds.project.repository.FraudReportRepository;
import kdt.fds.project.repository.FraudRepository;
import kdt.fds.project.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class FraudReportController {

    private final FraudReportRepository reportRepository;
    private final FraudRepository fraudRepository;
    private final TransactionRepository transactionRepository;

    // ==========================================
    // 1. 화면(View) 이동 매핑
    // ==========================================

    @GetMapping("/report/form")
    public String reportForm(@RequestParam(value = "account", required = false) String account, Model model) {
        model.addAttribute("reportedAccount", account);
        return "report/fraud_report";
    }

    @GetMapping("/report/list")
    public String reportList() {
        return "report/report_list";
    }

    @GetMapping("/report/unblock")
    public String unblockRequest() {
        return "report/unblock_request";
    }

    // ==========================================
    // 2. 데이터 처리(API) 매핑
    // ==========================================

    /**
     * [핵심] 신고 접수 API
     * 팀원이 정의한 FRAUD_REPORTS 테이블과 대시보드용 FRAUD_DETECTION_RESULTS를 동기화합니다.
     */
    @PostMapping("/api/reports")
    @ResponseBody
    public ResponseEntity<?> createReport(@RequestBody Map<String, Object> payload) {
        try {
            String account = (String) payload.get("account");
            String inputReasonCode = (String) payload.get("reason");

            log.info("🚨 신고 접수 시도 - 계좌: {}, 사유코드: {}", account, inputReasonCode);

            // [A] 팀원이 만든 FRAUD_REPORTS 테이블 저장 (신고 리스트 조회용)
            FraudReport report = FraudReport.builder()
                    .reportedAccount(account)
                    .accountNumber(account)
                    .reason(translateReason(inputReasonCode))
                    // AdminDashboardResponseDTO의 reasonCodeDistribution 대응을 위해 코드값도 저장 시도
                    // (엔티티에 필드가 있다면 저장되고, 없다면 무시됩니다.)
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();
            reportRepository.save(report);

            // [B] 사용자 대시보드 수치 연동 (FRAUD_DETECTION_RESULTS)
            // ORA-02291 에러를 피하기 위해 해당 계좌의 거래가 있는 경우에만 사기 결과로 등록합니다.
            transactionRepository.findFirstBySourceValueOrderByCreatedAtDesc(account)
                    .ifPresentOrElse(tx -> {
                        FraudDetectionResult detection = FraudDetectionResult.builder()
                                .txId(tx.getTxId())
                                .probability(1.0) // 신고는 확률 100% 사기로 간주
                                .isFraud(1)       // 이 값이 1이어야 대시보드의 '사기 확정' 수치가 올라감
                                .engine("USER_REPORT")
                                .thresholdValue(0.8)
                                .detectedAt(LocalDateTime.now())
                                .build();
                        fraudRepository.save(detection);
                        log.info("✅ 대시보드 반영 완료: 계좌 {} (거래 ID: {})", account, tx.getTxId());
                    }, () -> {
                        // 거래 내역이 없으면 외래 키 제약 조건 때문에 저장이 불가능함을 경고
                        log.warn("⚠️ 알림: 계좌 {}는 거래 내역(TRANSACTIONS)이 존재하지 않아 사용자 대시보드 수치에는 합산되지 않습니다.", account);
                    });

            return ResponseEntity.ok(Map.of("message", "신고가 정상 접수되었습니다.", "count", 1));

        } catch (Exception e) {
            log.error("❌ 신고 처리 중 오류 발생: ", e);
            return ResponseEntity.internalServerError().body(Map.of("message", "처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * [API] 신고 내역 데이터 제공 (report_list.html용)
     */
    @GetMapping("/api/reports/ranking")
    @ResponseBody
    public ResponseEntity<List<FraudReport>> getReportRanking() {
        return ResponseEntity.ok(reportRepository.findAll());
    }

    /**
     * 사유 코드 변환 헬퍼 메서드
     */
    private String translateReason(String code) {
        return switch (code) {
            case "1" -> "보이스피싱 의심";
            case "2" -> "대포통장 의심";
            case "3" -> "중고거래 사기";
            case "4" -> "검찰/기관 사칭";
            case "5" -> "메신저 피싱";
            default -> "기타 사유";
        };
    }
}