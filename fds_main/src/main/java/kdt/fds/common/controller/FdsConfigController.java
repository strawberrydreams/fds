package kdt.fds.common.controller;

import kdt.fds.common.entity.FdsConfig;
import kdt.fds.common.repository.FdsConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/config")
public class FdsConfigController {

    private final FdsConfigRepository configRepository;

    public FdsConfigController(FdsConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * 1. 모든 설정값 조회 (프론트에서 전체 설정을 한 번에 불러올 때 사용)
     * DB에 추가한 AUTO_APPROVE_AMOUNT를 포함한 모든 행을 반환합니다.
     */
    @GetMapping("/all")
    public ResponseEntity<List<FdsConfig>> getAllConfigs() {
        return ResponseEntity.ok(configRepository.findAll());
    }

    /**
     * 2. 범용 설정 업데이트 API
     * 어떤 키(configKey)든 받아서 값을 업데이트할 수 있습니다.
     */
    @PostMapping("/update")
    public ResponseEntity<?> updateConfig(@RequestBody Map<String, String> payload) {
        String key = payload.get("configKey");   // 예: "FRAUD_THRESHOLD" 또는 "AUTO_APPROVE_AMOUNT"
        String value = payload.get("configValue"); // 예: "0.85" 또는 "50000"

        if (key == null || value == null) {
            return ResponseEntity.badRequest().body("키 또는 값이 누락되었습니다.");
        }

        try {
            FdsConfig config = configRepository.findById(key)
                    .orElse(FdsConfig.builder().configKey(key).build());

            // 임계치(Slider) 전용 변환 로직 (필요 시)
            // 만약 프론트에서 0~100으로 보내고 key가 임계치라면 0.x로 변환
            if (key.equals("THRESHOLD") && Double.parseDouble(value) > 1.0) {
                value = String.valueOf(Double.parseDouble(value) / 100.0);
            }

            config.setConfigValue(value);
            config.setDescription("관리자 변경 반영");
            configRepository.save(config);

            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("오류 발생: " + e.getMessage());
        }
    }

    // 기존 프론트엔드 호환을 위한 유지
    @GetMapping("/threshold")
    public ResponseEntity<FdsConfig> getThreshold() {
        FdsConfig config = configRepository.findById("FRAUD_THRESHOLD")
                .orElseGet(() -> FdsConfig.builder()
                        .configKey("FRAUD_THRESHOLD")
                        .configValue("0.7")
                        .build());
        return ResponseEntity.ok(config);
    }
}
