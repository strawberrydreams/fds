package kdt.fds.project.controller;

import kdt.fds.project.entity.Account;
import kdt.fds.project.entity.Card;
import kdt.fds.project.entity.CardTransaction;
import kdt.fds.project.service.AccountService;
import kdt.fds.project.service.CardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/card")
public class CardController {

    private final CardService cardService;
    private final AccountService accountService;
    private final String TEST_USER = "testuser";

    // 1. 약관 페이지
    @GetMapping("/terms")
    public String showTermsPage() {
        return "card/terms";
    }

    // 2. OCR 인증 페이지
    @GetMapping("/auth")
    public String showAuthPage() {
        return "card/auth";
    }

    // 3. 신청 폼 페이지
    @GetMapping("/apply")
    public String showApplyPage(
            @RequestParam(name = "userName", required = false) String userName,
            @RequestParam(name = "userBirth", required = false) String userBirth,
            Model model) {
        if (userName == null || "미인식".equals(userName)) userName = "";
        if (userBirth == null || "미인식".equals(userBirth)) userBirth = "";
        model.addAttribute("accounts", accountService.getAccountsByLoginId(TEST_USER));
        model.addAttribute("userName", userName);
        model.addAttribute("userBirth", userBirth);
        return "card/apply";
    }

    // 4. 발급 처리
    @PostMapping("/issue")
    public String issueCard(@RequestParam String accountNumber,
                            @RequestParam String accountPassword,
                            @RequestParam String cardType,
                            RedirectAttributes redirectAttributes) {
        try {
            cardService.issueCard(TEST_USER, accountNumber, accountPassword, cardType);
            redirectAttributes.addFlashAttribute("message", "카드 발급이 완료되었습니다!");
            return "redirect:/card/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/card/apply";
        }
    }

    // 5. 카드 목록 페이지
    @GetMapping("/list")
    public String listCards(Model model) {
        model.addAttribute("cards", cardService.getCardsByLoginId(TEST_USER));
        return "card/list";
    }

    // 6. 카드 해지 전용 페이지 (여기서 모든 카드 목록을 보여줍니다)
    @GetMapping("/terminate")
    public String showTerminatePage(
            @RequestParam(name = "selectedCard", required = false) Long selectedCardId,
            Model model) {

        // 사용자의 전체 카드 목록 조회
        List<Card> myCards = cardService.getCardsByLoginId(TEST_USER);

        // 이미 해지된(TERMINATED) 카드는 제외하고 모델에 담기
        List<Card> activeCards = myCards.stream()
                .filter(c -> !"TERMINATED".equals(c.getStatus()))
                .toList();

        model.addAttribute("cards", activeCards);
        // 만약 리스트에서 특정 카드를 찍어서 왔다면, 해당 ID를 전달하여 기본 선택되게 함
        model.addAttribute("defaultCardId", selectedCardId);

        return "card/terminate";
    }

    // 7. 카드 상태 변경 (통합 처리)
    @PostMapping("/update-status")
    public String updateCardStatus(@RequestParam Long cardId,
                                   @RequestParam String status,
                                   @RequestParam(required = false) String password,
                                   RedirectAttributes redirectAttributes) {
        try {
            if ("ACTIVE".equals(status) || "TERMINATED".equals(status)) {
                cardService.updateCardStatusWithAuth(cardId, status, password);
            } else {
                cardService.updateCardStatus(cardId, status);
            }

            String statusKr = status.equals("LOST") ? "분실신고" : (status.equals("TERMINATED") ? "해지" : "정상");
            redirectAttributes.addFlashAttribute("message", "처리가 완료되었습니다. (상태: " + statusKr + ")");
        } catch (Exception e) {
            log.error("카드 상태 변경 에러: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "인증 실패: " + e.getMessage());

            // 해지 실패 시 다시 해지 페이지로 이동
            if ("TERMINATED".equals(status)) return "redirect:/card/terminate";
        }
        return "redirect:/card/list";
    }

    // 카드 결제 내역 조회 페이지
    @GetMapping("/history")
    public String showCardHistory(Model model) {
        // 1. 서비스에서 내 카드들의 모든 결제 내역을 가져옵니다.
        // (지금은 테스트를 위해 전체 리스트를 가져오는 메서드가 있다고 가정)
        List<CardTransaction> histories = cardService.getCardTransactionsByLoginId(TEST_USER);

        model.addAttribute("histories", histories);
        return "card/history";
    }


}