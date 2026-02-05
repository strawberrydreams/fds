package kdt.fds.account.controller;

import kdt.fds.account.entity.Account;
import kdt.fds.user.repository.UserRepository;
import kdt.fds.account.service.AccountService;
import kdt.fds.common.service.GoogleVisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/account")
public class AccountController {

    private final AccountService accountService;
    private final GoogleVisionService googleVisionService;
    private final UserRepository userRepository;

    /**
     * [REST API] 전체 계좌 목록 조회
     * 오류 해결: AccountService에 getAllAccounts() 메서드가 있어야 합니다.
     */
    @GetMapping("/api/list")
    @ResponseBody
    public ResponseEntity<List<Account>> getAllAccountsApi() {
        log.info("REST API: 모든 계좌 목록 조회 요청");
        List<Account> accounts = accountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    /**
     * [REST API] 계좌 직접 생성
     */
    @PostMapping("/api/create")
    @ResponseBody
    public ResponseEntity<?> createAccountApi(@RequestParam String userId,
                                              @RequestParam Long amount,
                                              @RequestParam String password) {
        log.info("REST API: 계좌 생성 요청 - ID: {}", userId);
        try {
            accountService.createAccount(userId, amount, password);
            return ResponseEntity.status(201).body("계좌 생성 성공");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("생성 실패: " + e.getMessage());
        }
    }

    // ==========================================
    // 1. 계좌 개설 뷰(View) 및 프로세스
    // ==========================================

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
    public String completeOcr(@RequestParam("imageData") String imageData, RedirectAttributes redirectAttributes) {
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
            // Principal을 통해 현재 로그인한 사용자 아이디 사용
            accountService.createAccount(principal.getName(), amount, password);
            redirectAttributes.addFlashAttribute("message", "계좌가 성공적으로 개설되었습니다.");
            return "redirect:/account/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "계좌 개설 실패: " + e.getMessage());
            return "redirect:/account/generation";
        }
    }

    // ==========================================
    // 2. 계좌 관리 및 거래 기능
    // ==========================================

    @GetMapping("/list")
    public String listAccounts(Principal principal, Model model) {
        List<Account> accounts = accountService.getAccountsByLoginId(principal.getName());
        model.addAttribute("accounts", accounts);
        return "account/list";
    }

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
            accountService.deposit(accountNumber, amount, password);
            redirectAttributes.addFlashAttribute("message", "입금 완료!");
            return "redirect:/account/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "입금 실패: " + e.getMessage());
            return "redirect:/account/deposit?accountNumber=" + accountNumber;
        }
    }

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

    // --- OCR 파싱 헬퍼 메서드 ---
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