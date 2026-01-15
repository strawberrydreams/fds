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
    private final GoogleVisionService googleVisionService; // Google 비전 서비스 주입
    private final String TEST_USER = "testuser";

    /**
     * 1. 계좌 개설 프로세스 (Terms -> OCR -> Generation -> Create)
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
    public String proceedToOcr(@RequestParam(name = "agree", defaultValue = "false") boolean agree,
                               Model model) {
        if (!agree) {
            model.addAttribute("error", "필수 약관에 동의하셔야 진행이 가능합니다.");
            return "account/terms";
        }
        return "account/ocr";
    }

    /**
     * Google Vision API 처리 로직 추가
     * ocr.html에서 전달한 base64 이미지 데이터를 처리함
     */
    @PostMapping("/ocr-complete")
    public String completeOcr(@RequestParam("imageDate") String imageData,
                              RedirectAttributes redirectAttributes) {
        log.info("Google Vision OCR 프로세스 시작");

        // 1. Google Vision을 통해 텍스트 추출
        String fullText = googleVisionService.extractTextFromImage(imageData);
        log.info("추출된 전체 텍스트: {}", fullText);

        // 2. 이름 및 생년월일 추출 로직 실행
        String scannedName = parseName(fullText);
        String scannedBirth = parseBirth(fullText);

        // 3. 결과 전달
        redirectAttributes.addFlashAttribute("scannedName", scannedName);
        redirectAttributes.addFlashAttribute("scannedBirth", scannedBirth);

        return "redirect:/account/generation";
    }

    /**
     * OCR 분석만 수행하여 JSON으로 결과 반환 (페이지 이동 없음)
     */
    @PostMapping("/ocr-api")
    @ResponseBody // HTML이 아닌 JSON 데이터를 직접 리턴함
    public Map<String, String> ocrApi(@RequestParam("imageDate") String imageData) {
        String fullText = googleVisionService.extractTextFromImage(imageData);

        Map<String, String> result = new HashMap<>();
        result.put("name", parseName(fullText));
        result.put("birth", parseBirth(fullText));

        return result; // { "name": "홍길동", "birth": "900101" }
    }

    @GetMapping("/generation")
    public String showGenerationPage(
            @RequestParam(value = "userName", required = false) String userName,
            @RequestParam(value = "userBirth", required = false) String userBirth,
            Model model) {

        // HTML에서 ${userName}, ${birthDate}로 부르기로 했으므로 이름을 맞춥니다.
        model.addAttribute("userName", userName);
        model.addAttribute("birthDate", userBirth);

        return "account/generation";
    }

    @PostMapping("/create")
    public String createAccount(
            @RequestParam String userName,
            @RequestParam String birthDate,
            @RequestParam Long amount,
            @RequestParam String password,
            RedirectAttributes redirectAttributes
    ) {
        try {
            // 실제 서비스에서는 userName과 birthDate도 유저 검증에 활용해야 함
            accountService.createAccount(TEST_USER, amount, password);
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
    public String listAccounts(Model model) {
        List<Account> accounts = accountService.getAccountsByLoginId(TEST_USER);
        model.addAttribute("accounts", accounts);
        return "account/list";
    }

    /**
     * 3. 입금 처리
     */
    @GetMapping("/deposit")
    public String showDepositPage(
            @RequestParam(name = "accountNumber", required = false) String accountNumber,
            Model model) {

        // 1. 현재 사용자의 모든 활성 계좌 목록을 가져옵니다. (드롭다운을 채우기 위해 무조건 필요)
        List<Account> myAccounts = accountService.getAccountsByLoginId(TEST_USER);

        // 2. 만약 계좌가 하나도 없다면 계좌 개설 페이지로 안내합니다.
        if (myAccounts.isEmpty()) {
            return "redirect:/account/generation";
        }

        // 3. 모델에 데이터 담기
        model.addAttribute("accounts", myAccounts);      // 드롭다운 리스트용
        model.addAttribute("accountNumber", accountNumber); // 리스트에서 전달받은 선택 값 (없으면 null)

        return "account/deposit";
    }

    @PostMapping("/deposit")
    public String processDeposit(@RequestParam String accountNumber,
                                 @RequestParam Long amount,
                                 @RequestParam String password,
                                 RedirectAttributes redirectAttributes) {
        try {
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
    /**
     * 4. 송금 처리 화면
     */
    /**
     * 4. 송금 처리 화면
     * @RequestParam을 통해 리스트에서 넘어오는 accountNumber를 받을 수 있게 수정했습니다.
     */
    @GetMapping("/transfer")
    public String showTransferPage(
            @RequestParam(name = "accountNumber", required = false) String accountNumber,
            Model model) {

        // 1. 내 계좌 목록 조회
        List<Account> myAccounts = accountService.getAccountsByLoginId(TEST_USER);

        // 2. 최근 송금 내역 조회
        List<Transaction> transferHistory = accountService.getRecentTransferHistory(TEST_USER);

        // 3. 총 누적 송금액 조회
        Long totalTransferAmount = accountService.getTotalTransferAmount(TEST_USER);

        // 4. 모델에 데이터 담기
        model.addAttribute("accounts", myAccounts);
        model.addAttribute("transferHistory", transferHistory);
        model.addAttribute("totalTransferAmount", totalTransferAmount != null ? totalTransferAmount : 0L);

        // [중요] 리스트에서 선택해서 들어온 경우, 해당 계좌번호를 fromAccNum에 할당하여 자동 선택되게 함
        model.addAttribute("fromAccNum", accountNumber);

        return "account/transfer";
    }

    @PostMapping("/transfer")
    public String processTransfer(
            @RequestParam String fromAccNum,
            @RequestParam String toAccNum,
            @RequestParam Long amount,
            @RequestParam String password,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            accountService.transfer(fromAccNum, toAccNum, amount, password);
            redirectAttributes.addFlashAttribute("message", "송금이 완료되었습니다.");
            return "redirect:/account/list";
        } catch (Exception e) {
            model.addAttribute("error", "송금 실패: " + e.getMessage());
            model.addAttribute("accounts", accountService.getAccountsByLoginId(TEST_USER));
            model.addAttribute("transferHistory", accountService.getRecentTransferHistory(TEST_USER));
            return "account/transfer";
        }
    }

    /**
     * 5. 인출 처리
     */
    @GetMapping("/withdraw")
    public String showWithdrawPage(@RequestParam(name = "accountNumber", required = false) String accountNumber, Model model) {
        model.addAttribute("accounts", accountService.getAccountsByLoginId(TEST_USER));
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("withdrawHistory", accountService.getRecentWithdrawHistory(TEST_USER));
        Long totalWithdrawAmount = accountService.getTotalWithdrawAmount(TEST_USER);
        model.addAttribute("totalWithdrawAmount", totalWithdrawAmount);
        return "account/withdraw";
    }

    @PostMapping("/withdraw")
    public String processWithdraw(@RequestParam String accountNumber,
                                  @RequestParam Long amount,
                                  @RequestParam String password,
                                  RedirectAttributes redirectAttributes) {
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

    // --- 헬퍼 메서드: 이름 및 생년월일 파싱 ---

    private String parseName(String text) {
        if (text == null || text.isEmpty()) return "미인식";

        // 1. 공백 제거
        String cleanText = text.replace(" ", "");

        // 2. '성명' 혹은 '이름' 뒤의 2~4자 추출 시도
        Pattern p1 = Pattern.compile("(성명|이름)[:\\s]*([가-힣]{2,4})");
        Matcher m1 = p1.matcher(cleanText);
        if (m1.find()) return m1.group(2);

        // 3. (실패 시) 주민등록증 상단 '주민등록증' 단어를 제외하고 가장 먼저 나오는 2~4자 한글 찾기
        String filtered = cleanText.replace("주민등록증", "").replace("운전면허증", "");
        Pattern p2 = Pattern.compile("[가-힣]{2,4}");
        Matcher m2 = p2.matcher(filtered);

        return m2.find() ? m2.group() : "미인식";
    }

    private String parseBirth(String text) {
        if (text == null || text.isEmpty()) return "미인식";
        // 생년월일 6자리 (숫자 연속 6개)
        Pattern pattern = Pattern.compile("\\d{6}");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : "미인식";
    }
}