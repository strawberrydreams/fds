package kdt.fds.project.controller;

import kdt.fds.project.service.CardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PaymentController {

    private final CardService cardService;

    // 결제 테스트 화면 (시뮬레이터)
    @GetMapping("/sim")
    public String showPaymentSim() {
        return "card/pay-sim";
    }

    // 결제 실행 요청 처리
    @PostMapping("/execute")
    public String executePayment(@RequestParam String cardNumber,
                                 @RequestParam Long amount,
                                 @RequestParam String merchantName,
                                 RedirectAttributes redirectAttributes) {
        try {
            // 카드 번호를 기반으로 결제를 처리하므로 별도의 Principal이 없어도 작동합니다.
            cardService.processPayment(cardNumber, amount, merchantName);

            redirectAttributes.addFlashAttribute("message", "결제가 완료되었습니다!");
            return "redirect:/card/history";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "결제 실패: " + e.getMessage());
            return "redirect:/pay/sim";
        }
    }
}