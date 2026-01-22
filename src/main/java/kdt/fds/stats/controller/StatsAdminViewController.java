package kdt.fds.stats.controller;

import java.time.LocalDate;
import java.util.List;
import kdt.fds.stats.dto.request.StatsSnapshotGenerateRequestDTO;
import kdt.fds.stats.dto.response.AdminDashboardResponseDTO;
import kdt.fds.stats.dto.response.StatsSnapshotMetadataDTO;
import kdt.fds.stats.service.AdminStatsDashboardService;
import kdt.fds.stats.service.StatsSnapshotService;
import kdt.fds.stats.vo.StatsSnapshotScope;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 이 파일은 관리자 통계 Thymeleaf 뷰 컨트롤러 파일이다.
 * 관리자 대시보드와 스냅샷 관리 페이지를 렌더링한다.
 */
@Controller
@RequestMapping("/stats/admin")
@PreAuthorize("hasRole('ADMIN')")
public class StatsAdminViewController {
    private final AdminStatsDashboardService adminDashboardService;
    private final StatsSnapshotService snapshotService;

    public StatsAdminViewController(
            AdminStatsDashboardService adminDashboardService,
            StatsSnapshotService snapshotService
    ) {
        this.adminDashboardService = adminDashboardService;
        this.snapshotService = snapshotService;
    }

    /**
     * 관리자 대시보드 페이지를 렌더링한다.
     * 10개 섹션의 집계 데이터를 모델에 추가한다.
     */
    @GetMapping("/dashboard")
    public String adminDashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,
            @RequestParam(defaultValue = "overview") String tab,
            Model model
    ) {
        AdminDashboardResponseDTO dashboard = adminDashboardService.getAdminDashboard(fromDate, toDate);

        model.addAttribute("dashboard", dashboard);
        model.addAttribute("fromDate", dashboard.range().fromDate());
        model.addAttribute("toDate", dashboard.range().toDate());
        model.addAttribute("activeTab", tab);

        return "stats/admindashboard";
    }

    /**
     * 관리자 스냅샷 관리 페이지를 렌더링한다.
     * 비즈니스 스냅샷 목록을 모델에 추가한다.
     */
    @GetMapping("/snapshots")
    public String adminSnapshots(
            @RequestParam(required = false) String snapshotId,
            Model model
    ) {
        List<StatsSnapshotMetadataDTO> snapshots = snapshotService.listSnapshots(StatsSnapshotScope.BUSINESS);
        model.addAttribute("snapshots", snapshots);

        if (snapshotId != null && !snapshotId.isBlank()) {
            String filename = snapshots.stream()
                    .filter(s -> s.snapshotId().equals(snapshotId))
                    .map(StatsSnapshotMetadataDTO::filename)
                    .findFirst()
                    .orElse(null);

            if (filename != null) {
                Object detail = snapshotService.getSnapshotDetailByFilename(StatsSnapshotScope.BUSINESS, filename);
                model.addAttribute("selectedSnapshotId", snapshotId);
                model.addAttribute("snapshotDetail", detail);
            }
        }

        return "stats/adminsnapshots";
    }

    /**
     * 스냅샷 생성을 처리한다.
     * 생성 결과 메시지를 Flash Attribute로 전달한다.
     */
    @PostMapping("/snapshots/generate")
    public String generateSnapshot(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,
            @RequestParam(defaultValue = "false") boolean forceRebuild,
            RedirectAttributes redirectAttributes
    ) {
        try {
            StatsSnapshotGenerateRequestDTO request = new StatsSnapshotGenerateRequestDTO(
                    fromDate, toDate, forceRebuild
            );
            snapshotService.generate(request);
            redirectAttributes.addFlashAttribute("successMessage",
                    "스냅샷이 생성되었습니다. (" + fromDate + " ~ " + toDate + ")");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "스냅샷 생성 실패: " + ex.getMessage());
        }

        return "redirect:/stats/admin/snapshots";
    }
}
