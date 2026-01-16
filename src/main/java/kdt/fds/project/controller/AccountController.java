package kdt.fds.project.controller;

import kdt.fds.project.entity.Account;
import kdt.fds.project.entity.Transaction;
import kdt.fds.project.service.AccountService;
import kdt.fds.project.service.GoogleVisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/account")
public class AccountController {

    private final AccountService accountService;
    private final GoogleVisionService googleVisionService;

    /**
     * 1. 계좌 개설 프로세스
     */
    @GetMapping("/terms")
    public String showTermsPage() {
        return "account/terms";
    }

    @GetMapping("/ocr")
    public String showOcrPage() {
        return "account/ocr";
    }

    @PostMapping("/ocr")
    public String proceedToOcr(@RequestParam(name = "agree", defaultValue = "false") boolean agree, Model model) {
        if (!agree) {
            model.addAttribute("error", "필수 약관에 동의하셔야 진행이 가능합니다.");
            return "account/terms";
        }
        return "account/ocr";
    }

    @PostMapping("/ocr-complete")
    public String completeOcr(@RequestParam("imageDate") String imageData, RedirectAttributes redirectAttributes) {
        String fullText = googleVisionService.extractTextFromImage(imageData);
        String scannedName = parseName(fullText);
        String scannedBirth = parseBirth(fullText);

        redirectAttributes.addFlashAttribute("scannedName", scannedName);
        redirectAttributes.addFlashAttribute("scannedBirth", scannedBirth);
        return "redirect:/account/generation";
    }

    @GetMapping("/generation")
    public String showGenerationPage(@ModelAttribute("scannedName") String scannedName,
                                     @ModelAttribute("scannedBirth") String scannedBirth, Model model) {
        model.addAttribute("userName", scannedName);
        model.addAttribute("birthDate", scannedBirth);
        return "account/generation";
    }

    @PostMapping("/create")
    public String createAccount(@RequestParam String userName, @RequestParam String birthDate,
                                @RequestParam Long amount, @RequestParam String password,
                                Principal principal, RedirectAttributes redirectAttributes) {
        try {
            accountService.createAccount(principal.getName(), amount, password);
            redirectAttributes.addFlashAttribute("message", "계좌가 성공적으로 개설되었습니다.");
            return "redirect:/account/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "계좌 개설 실패: " + e.getMessage());
            return "redirect:/account/generation";
        }
    }

    /**
     * 2. 계좌 목록 조회
     */
    @GetMapping("/list")
    public String listAccounts(Principal principal, Model model) {
        List<Account> accounts = accountService.getAccountsByLoginId(principal.getName());
        model.addAttribute("accounts", accounts);
        return "account/list";
    }

    /**
     * 3. 입금 처리 (에러 발생 지점 관련)
     */
    @GetMapping("/deposit")
    public String showDepositPage(@RequestParam(name = "accountNumber", required = false) String accountNumber,
                                  Principal principal, Model model) {
        List<Account> myAccounts = accountService.getAccountsByLoginId(principal.getName());
        if (myAccounts.isEmpty()) return "redirect:/account/generation";

        model.addAttribute("accounts", myAccounts);
        model.addAttribute("accountNumber", accountNumber);
        return "account/deposit";
    }

    @PostMapping("/deposit")
    public String processDeposit(@RequestParam String accountNumber, @RequestParam Long amount,
                                 @RequestParam String password, RedirectAttributes redirectAttributes) {
        try {
            // Service 내에서 Transaction 엔티티 생성 시 amount 세팅 여부 확인 필요
            accountService.deposit(accountNumber, amount, password);
            redirectAttributes.addFlashAttribute("message", "입금 완료!");
            return "redirect:/account/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "입금 실패: " + e.getMessage());
            return "redirect:/account/deposit?accountNumber=" + accountNumber;
        }
    }

    /**
     * 4. 송금 처리
     */
    @GetMapping("/transfer")
    public String showTransferPage(@RequestParam(name = "accountNumber", required = false) String accountNumber,
                                   Principal principal, Model model) {
        String loginId = principal.getName();
        model.addAttribute("accounts", accountService.getAccountsByLoginId(loginId));
        model.addAttribute("transferHistory", accountService.getRecentTransferHistory(loginId));
        model.addAttribute("totalTransferAmount", accountService.getTotalTransferAmount(loginId));
        model.addAttribute("fromAccNum", accountNumber);
        return "account/transfer";
    }

    @PostMapping("/transfer")
    public String processTransfer(@RequestParam String fromAccNum, @RequestParam String toAccNum,
                                  @RequestParam Long amount, @RequestParam String password,
                                  Principal principal, Model model, RedirectAttributes redirectAttributes) {
        try {
            accountService.transfer(fromAccNum, toAccNum, amount, password);
            redirectAttributes.addFlashAttribute("message", "송금이 완료되었습니다.");
            return "redirect:/account/list";
        } catch (Exception e) {
            model.addAttribute("error", "송금 실패: " + e.getMessage());
            model.addAttribute("accounts", accountService.getAccountsByLoginId(principal.getName()));
            return "account/transfer";
        }
    }

    /**
     * 5. 인출 처리
     */
    @GetMapping("/withdraw")
    public String showWithdrawPage(@RequestParam(name = "accountNumber", required = false) String accountNumber,
                                   Principal principal, Model model) {
        String loginId = principal.getName();
        model.addAttribute("accounts", accountService.getAccountsByLoginId(loginId));
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("withdrawHistory", accountService.getRecentWithdrawHistory(loginId));
        model.addAttribute("totalWithdrawAmount", accountService.getTotalWithdrawAmount(loginId));
        return "account/withdraw";
    }

    @PostMapping("/withdraw")
    public String processWithdraw(@RequestParam String accountNumber, @RequestParam Long amount,
                                  @RequestParam String password, RedirectAttributes redirectAttributes) {
        try {
            accountService.withdraw(accountNumber, amount, password);
            redirectAttributes.addFlashAttribute("message", "인출 완료!");
            return "redirect:/account/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "인출 실패: " + e.getMessage());
            return "redirect:/account/withdraw?accountNumber=" + accountNumber;
        }
    }

    /**
     * 6. 계좌 해지
     */
    @PostMapping("/delete")
    public String deleteAccount(@RequestParam("accountId") Long accountId, RedirectAttributes redirectAttributes) {
        try {
            accountService.deleteAccount(accountId);
            redirectAttributes.addFlashAttribute("message", "계좌가 정상적으로 해지되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/account/list";
    }

    // --- 헬퍼 메서드 ---
    private String parseName(String text) {
        if (text == null || text.isEmpty()) return "미인식";
        String cleanText = text.replace(" ", "");
        Pattern p1 = Pattern.compile("(성명|이름)[:\\s]*([가-힣]{2,4})");
        Matcher m1 = p1.matcher(cleanText);
        if (m1.find()) return m1.group(2);
        return "미인식";
    }

    private String parseBirth(String text) {
        if (text == null || text.isEmpty()) return "미인식";
        Pattern pattern = Pattern.compile("\\d{6}");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : "미인식";
    }
}