package kdt.fds.stats.controller;

import java.security.Principal;
import java.util.List;
import kdt.fds.stats.dto.response.StatsSnapshotMetadataDTO;
import kdt.fds.stats.dto.response.UserDashboardResponseDTO;
import kdt.fds.stats.dto.response.UserSummaryResponseDTO;
import kdt.fds.stats.service.StatsSnapshotService;
import kdt.fds.stats.service.UserStatsDashboardService;
import kdt.fds.stats.vo.StatsSnapshotScope;
import kdt.fds.stats.vo.StatsRangeType;
import kdt.fds.project.entity.User;
import kdt.fds.project.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Controller
@RequestMapping("/stats")
public class StatsViewController {
    private final UserStatsDashboardService userDashboardService;
    private final StatsSnapshotService snapshotService;
    private final UserRepository userRepository;

    public StatsViewController(
            UserStatsDashboardService userDashboardService,
            StatsSnapshotService snapshotService,
            UserRepository userRepository
    ) {
        this.userDashboardService = userDashboardService;
        this.snapshotService = snapshotService;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public String userDashboard(
            Principal principal,
            @RequestParam(defaultValue = "LAST_7_DAYS") StatsRangeType range,
            Model model
    ) {
        // 1. 로그인한 사용자의 PK 조회
        Long userId = resolveUserId(principal);
        log.info("📊 대시보드 조회 요청 - 사용자 PK: {}, 조회 범위: {}", userId, range);

        // 2. 서비스 호출 (조인 구조가 개선된 서비스여야 함)
        UserSummaryResponseDTO summary = userDashboardService.getUserSummary(userId, range);
        UserDashboardResponseDTO dashboard = userDashboardService.getUserDashboard(userId, range);

        // 3. 모델 담기
        model.addAttribute("summary", summary);
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("range", range);
        model.addAttribute("rangeLabel", range == StatsRangeType.TODAY ? "오늘" : "최근 7일");

        return "stats/userdashboard";
    }

    @GetMapping("/snapshots")
    public String userSnapshots(
            @RequestParam(required = false) String snapshotId,
            Model model
    ) {
        List<StatsSnapshotMetadataDTO> snapshots = snapshotService.listSnapshots(StatsSnapshotScope.GENERAL);
        model.addAttribute("snapshots", snapshots);

        if (snapshotId != null && !snapshotId.isBlank()) {
            String filename = snapshots.stream()
                    .filter(s -> s.snapshotId().equals(snapshotId))
                    .map(StatsSnapshotMetadataDTO::filename)
                    .findFirst()
                    .orElse(null);

            if (filename != null) {
                Object detail = snapshotService.getSnapshotDetailByFilename(StatsSnapshotScope.GENERAL, filename);
                model.addAttribute("selectedSnapshotId", snapshotId);
                model.addAttribute("snapshotDetail", detail);
            }
        }
        return "stats/usersnapshots";
    }

    private Long resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증되지 않은 사용자입니다.");
        }
        String loginId = principal.getName();
        log.debug("현재 로그인 ID: {}", loginId);

        User user = userRepository.findByUserId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return user.getId();
    }
}