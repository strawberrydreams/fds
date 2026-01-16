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

import java.security.Principal;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/card") // 다시 /card로 경로를 지정하여 FormController와 충돌을 방지합니다.
public class CardController {

    private final CardService cardService;
    private final AccountService accountService;

    /**
     * [삭제됨] @GetMapping("/") 메서드는 FormController에 이미 있으므로 여기서 중복으로 만들지 않습니다.
     */

    /**
     * 1. 카드 발급 프로세스
     */
    @GetMapping("/terms")
    public String showTermsPage() {
        return "card/terms";
    }

    @GetMapping("/auth")
    public String showAuthPage() {
        return "card/auth";
    }

    @GetMapping("/apply")
    public String showApplyPage(
            @RequestParam(name = "userName", required = false) String userName,
            @RequestParam(name = "userBirth", required = false) String userBirth,
            Principal principal,
            Model model) {

        if (userName == null || "미인식".equals(userName)) userName = "";
        if (userBirth == null || "미인식".equals(userBirth)) userBirth = "";

        // Principal을 사용하여 실제 로그인한 사용자의 계좌만 조회
        model.addAttribute("accounts", accountService.getAccountsByLoginId(principal.getName()));
        model.addAttribute("userName", userName);
        model.addAttribute("userBirth", userBirth);
        return "card/apply";
    }

    @PostMapping("/issue")
    public String issueCard(@RequestParam String accountNumber,
                            @RequestParam String accountPassword,
                            @RequestParam String cardType,
                            Principal principal,
                            RedirectAttributes redirectAttributes) {
        try {
            // 실제 로그인한 사용자 아이디 사용
            cardService.issueCard(principal.getName(), accountNumber, accountPassword, cardType);
            redirectAttributes.addFlashAttribute("message", "카드 발급이 완료되었습니다!");
            return "redirect:/card/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/card/apply";
        }
    }

    /**
     * 2. 카드 목록 및 상태 관리
     */
    @GetMapping("/list")
    public String listCards(Principal principal, Model model) {
        // 본인의 카드만 조회
        model.addAttribute("cards", cardService.getCardsByLoginId(principal.getName()));
        return "card/list";
    }

    @GetMapping("/terminate")
    public String showTerminatePage(
            @RequestParam(name = "selectedCard", required = false) Long selectedCardId,
            Principal principal,
            Model model) {

        List<Card> myCards = cardService.getCardsByLoginId(principal.getName());
        List<Card> activeCards = myCards.stream()
                .filter(c -> !"TERMINATED".equals(c.getStatus()))
                .toList();

        model.addAttribute("cards", activeCards);
        model.addAttribute("defaultCardId", selectedCardId);
        return "card/terminate";
    }

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
            redirectAttributes.addFlashAttribute("message", "처리가 완료되었습니다.");
        } catch (Exception e) {
            log.error("카드 상태 변경 에러: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "인증 실패: " + e.getMessage());
            if ("TERMINATED".equals(status)) return "redirect:/card/terminate";
        }
        return "redirect:/card/list";
    }

    /**
     * 3. 카드 내역 확인
     */
    @GetMapping("/history")
    public String showCardHistory(Principal principal, Model model) {
        // 본인의 결제 내역 조회
        List<CardTransaction> histories = cardService.getCardTransactionsByLoginId(principal.getName());
        model.addAttribute("histories", histories);
        return "card/history";
    }
}