package kdt.fds.project.controller;

import kdt.fds.project.service.CardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 가상 결제 시뮬레이터 컨트롤러
 * 가맹점에서 결제가 일어나는 상황을 테스트하기 위한 용도입니다.
 */
@Controller
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PaymentController {

    private final CardService cardService;

    // 결제 테스트 화면 (키오스크/결제창 시뮬레이터)
    @GetMapping("/sim")
    public String showPaymentSim() {
        return "card/pay-sim"; // templates/card/pay-sim.html
    }

    // 결제 실행 요청 처리
    @PostMapping("/execute")
    public String executePayment(@RequestParam String cardNumber,
                                 @RequestParam Long amount,
                                 @RequestParam String merchantName,
                                 RedirectAttributes redirectAttributes) {
        try {
            // CardService에서 실제 잔액 차감 및 내역 저장을 처리합니다.
            cardService.processPayment(cardNumber, amount, merchantName);

            // 성공 시 메시지와 함께 카드 이용 내역 페이지로 리다이렉트
            redirectAttributes.addFlashAttribute("message", "결제가 완료되었습니다!");
            return "redirect:/card/history";

        } catch (Exception e) {
            // 잔액 부족, 정지 카드 등 실패 시 에러 메시지와 함께 시뮬레이터로 복귀
            redirectAttributes.addFlashAttribute("error", "결제 실패: " + e.getMessage());
            return "redirect:/pay/sim";
        }
    }
}