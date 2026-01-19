package kdt.fds.project.controller;

import kdt.fds.project.entity.FraudReport;
import kdt.fds.project.repository.FraudReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class FraudReportController {

    private final FraudReportRepository reportRepository;

    // [설정] 사유 코드 매핑 (나중에 DB나 Enum으로 빼도 됩니다)
    private static final Map<Integer, String> REASON_MAP = Map.of(
            1, "보이스피싱 의심",
            2, "대포통장 의심",
            3, "중고거래 사기",
            4, "검찰/기관 사칭",
            5, "가족/지인 사칭 메신저 피싱"
    );
    //신고 랭킹 조회 API
    @GetMapping("/ranking")
    public ResponseEntity<List<FraudReport>> getReportRanking() {
        // 신고 횟수(reportCount)가 많은 순서대로 정렬해서 가져옵니다.
        List<FraudReport> ranking = reportRepository.findAllByOrderByReportCountDesc();
        return ResponseEntity.ok(ranking);
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲
    @PostMapping
    public ResponseEntity<?> createReport(@RequestBody Map<String, Object> payload) {
        try {
            String account = (String) payload.get("account");
            String inputReason = (String) payload.get("reason");

            // --- [사유 코드 매핑 로직 (기존과 동일)] ---
            int code = 0;
            String mappedReason = inputReason;

            if (inputReason.matches("\\d+")) {
                int inputCode = Integer.parseInt(inputReason);
                if (REASON_MAP.containsKey(inputCode)) {
                    code = inputCode;
                    mappedReason = REASON_MAP.get(inputCode);
                }
            } else {
                // 텍스트 입력 시 키워드 분석 (간단 버전)
                if (inputReason.contains("보이스")) code = 1;
                else if (inputReason.contains("대포")) code = 2;
            }
            // ------------------------------------------


            // [핵심 변경] 이미 신고된 계좌인지 확인
            Optional<FraudReport> existingReport = reportRepository.findByReportedAccount(account);

            if (existingReport.isPresent()) {
                // Case A: 이미 신고된 적 있음 -> 카운트 증가 & 정보 갱신
                FraudReport report = existingReport.get();

                int newCount = report.getReportCount() + 1;
                report.setReportCount(newCount);

                // 사유를 최신 것으로 업데이트하거나, 이어 붙일 수 있음.
                // 여기서는 "기존 사유 + (추가신고)" 형태로 갱신해 보겠습니다.
                report.setReason(report.getReason() + " / " + mappedReason);
                report.setReasonCode(code); // 최신 코드로 갱신
                report.setCreatedAt(LocalDateTime.now()); // 신고 시간 갱신

                reportRepository.save(report);

                log.info("🚨 신고 누적 - 계좌: {}, 누적횟수: {}", account, newCount);
                return ResponseEntity.ok("신고가 누적되었습니다. (현재 " + newCount + "회 신고됨)");

            } else {
                // Case B: 첫 신고 -> 신규 생성
                FraudReport report = FraudReport.builder()
                        .reportedAccount(account)
                        .reason(mappedReason)
                        .reasonCode(code)
                        .reporterId(1L)
                        .reportCount(1) // 최초 1회
                        .status("PENDING")
                        .build();

                reportRepository.save(report);
                log.info("🚨 신규 신고 접수 - 계좌: {}", account);
                return ResponseEntity.ok("신고가 접수되었습니다. (1회)");
            }

        } catch (Exception e) {
            log.error("신고 에러", e);
            return ResponseEntity.internalServerError().body("오류 발생");
        }
    }
}