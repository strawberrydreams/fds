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
@RequestMapping("/card")
public class CardController {

    private final CardService cardService;
    private final AccountService accountService;

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
     * 3. 카드 내역 확인 및 시뮬레이터
     */
    @GetMapping("/history")
    public String showCardHistory(Principal principal, Model model) {
        List<CardTransaction> histories = cardService.getCardTransactionsByLoginId(principal.getName());
        model.addAttribute("histories", histories);
        return "card/history";
    }

    // [수정] 템플릿 경로를 실제 파일 위치에 맞춰 수정했습니다.
    // 만약 pay-sim.html이 templates/card/pay-sim.html 에 있다면 "card/pay-sim"으로 리턴해야 합니다.
    @GetMapping("/simulator")
    public String showSimulatorPage(Model model) {
        model.addAttribute("title", "가상 결제 시뮬레이터");
        return "card/pay-sim";
    }

    // 가상 결제 실행 로직
    @PostMapping("/execute-pay")
    public String executePayment(@RequestParam String cardNumber,
                                 @RequestParam String merchantName,
                                 @RequestParam Long amount,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        try {
            cardService.createTransactionByLoginId(principal.getName(), cardNumber, merchantName, amount);
            redirectAttributes.addFlashAttribute("message", "결제가 완료되었습니다.");
            return "redirect:/card/history";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "결제 실패: " + e.getMessage());
            return "redirect:/card/simulator";
        }
    }
}