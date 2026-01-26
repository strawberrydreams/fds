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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * 이 파일은 사용자 통계 Thymeleaf 뷰 컨트롤러 파일이다.
 * 사용자 대시보드와 스냅샷 페이지를 렌더링한다.
 */
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

    /**
     * 사용자 대시보드 페이지를 렌더링한다.
     * 요약 KPI와 상세 대시보드 데이터를 모델에 추가한다.
     */
    @GetMapping("/dashboard")
    public String userDashboard(
            Principal principal,
            @RequestParam(defaultValue = "LAST_7_DAYS") StatsRangeType range,
            Model model
    ) {
        Long userId = resolveUserId(principal);

        UserSummaryResponseDTO summary = userDashboardService.getUserSummary(userId, range);
        UserDashboardResponseDTO dashboard = userDashboardService.getUserDashboard(userId, range);

        model.addAttribute("summary", summary);
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("range", range);
        model.addAttribute("rangeLabel", range == StatsRangeType.TODAY ? "오늘" : "최근 7일");

        return "stats/userdashboard";
    }

    /**
     * 사용자 스냅샷 히스토리 페이지를 렌더링한다.
     * 스냅샷 목록과 선택된 스냅샷 상세를 모델에 추가한다.
     */
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

    /**
     * 인증 Principal에서 User 엔티티의 ID(Long)를 조회한다.
     */
    private Long resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String loginId = principal.getName();
        if (loginId.isBlank() || "anonymousUser".equals(loginId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        User user = userRepository.findByUserId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return user.getId();
    }
}
