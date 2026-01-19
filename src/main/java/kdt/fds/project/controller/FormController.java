package kdt.fds.project.controller;

// [수정] DTO 패키지로 경로가 변경됨
import kdt.fds.project.dto.CombinedTransactionDTO;
import kdt.fds.project.dto.MemberDTO;
import kdt.fds.project.mapper.UserMapper;
import kdt.fds.project.entity.Account;
import kdt.fds.project.entity.Card;
import kdt.fds.project.entity.Transaction;
import kdt.fds.project.entity.CardTransaction;
import kdt.fds.project.service.AccountService;
import kdt.fds.project.service.CardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
public class FormController {

    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AccountService accountService;
    private final CardService cardService;

    public FormController(PasswordEncoder passwordEncoder, UserMapper userMapper,
                          AccountService accountService, CardService cardService) {
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.accountService = accountService;
        this.cardService = cardService;
    }

    @GetMapping("/")
    public String index(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getName().equals("anonymousUser")) {
            model.addAttribute("loginUser", authentication.getName());
        }
        return "index";
    }

    @GetMapping("/mypage")
    public String myPage(@AuthenticationPrincipal User user, Model model) {
        if (user == null) return "redirect:/login";

        String loginId = user.getUsername();
        log.info("=== 마이페이지 데이터 로딩 시작: {} ===", loginId);

        try {
            // 기존 기본 정보 세팅
            model.addAttribute("member", userMapper.findByUserId(loginId));
            List<Account> accounts = accountService.getAccountsByLoginId(loginId);
            model.addAttribute("totalBalance", accounts != null ? accounts.stream().mapToLong(Account::getBalance).sum() : 0);
            model.addAttribute("accountCount", accounts != null ? accounts.size() : 0);

            List<Card> myCards = cardService.getCardsByLoginId(loginId);
            model.addAttribute("cards", myCards);
            model.addAttribute("cardCount", myCards != null ? myCards.size() : 0);

            // --- 통합 거래 내역 로직 (디버깅 강화) ---
            List<CombinedTransactionDTO> combinedList = new ArrayList<>();

            // 1. 계좌 내역 추가
            List<Transaction> accountHistories = accountService.getRecentTransactionsByLoginId(loginId);
            if (accountHistories != null) {
                log.info("계좌 내역 개수: {}", accountHistories.size());
                for (Transaction tx : accountHistories) {
                    combinedList.add(new CombinedTransactionDTO(
                            tx.getCreatedAt(), tx.getDescription(), tx.getAmount(), "계좌", "SUCCESS"
                    ));
                }
            }

            // 2. 카드 내역 추가
            List<CardTransaction> cardHistories = cardService.getCardTransactionsByLoginId(loginId);
            if (cardHistories != null) {
                log.info("카드 내역 개수: {}", cardHistories.size());
                for (CardTransaction ctx : cardHistories) {
                    combinedList.add(new CombinedTransactionDTO(
                            ctx.getApprovedAt(), ctx.getMerchantName(), ctx.getAmount(), "카드", ctx.getStatus()
                    ));
                }
            }

            // 3. 통합 및 정렬 (데이터가 있을 때만 실행)
            if (!combinedList.isEmpty()) {
                List<CombinedTransactionDTO> recentTransactions = combinedList.stream()
                        .filter(tx -> tx.getTransactionDate() != null) // 날짜가 null인 데이터 제외
                        .sorted(Comparator.comparing(CombinedTransactionDTO::getTransactionDate).reversed())
                        .limit(5)
                        .collect(Collectors.toList());

                log.info("최종 통합 내역 개수: {}", recentTransactions.size());
                model.addAttribute("recentTransactions", recentTransactions);
            } else {
                log.warn("통합할 거래 내역이 하나도 없습니다.");
                model.addAttribute("recentTransactions", new ArrayList<>());
            }

        } catch (Exception e) {
            log.error("마이페이지 데이터 통합 중 에러 발생: ", e);
        }
        return "mypage/mypage";
    }

    @GetMapping("/login")
    public String loginForm() { return "login/login"; }

    @GetMapping("/find-account")
    public String findAccountPage() { return "login/find_account"; }

    @GetMapping("/join")
    public String showForm(Model model) {
        model.addAttribute("memberDTO", new MemberDTO());
        return "login/join";
    }

    @PostMapping("/join")
    public String submitForm(@ModelAttribute MemberDTO memberDTO) {
        memberDTO.setUserPw(passwordEncoder.encode(memberDTO.getUserPw()));
        userMapper.save(memberDTO);
        return "login/join_result";
    }

    @GetMapping("/mypage/confirm")
    public String confirmPage() { return "mypage/mypage_confirm"; }

    @PostMapping("/mypage/confirm")
    public String confirmPassword(@RequestParam String currentPw, @AuthenticationPrincipal User user, RedirectAttributes rttr) {
        MemberDTO member = userMapper.findByUserId(user.getUsername());
        if (passwordEncoder.matches(currentPw, member.getUserPw())) {
            return "redirect:/mypage/edit";
        } else {
            rttr.addFlashAttribute("msg", "비밀번호가 일치하지 않습니다.");
            return "redirect:/mypage/confirm";
        }
    }

    @GetMapping("/mypage/edit")
    public String editPage(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("member", userMapper.findByUserId(user.getUsername()));
        return "mypage/mypage_edit";
    }

    @PostMapping("/mypage/update")
    public String updateInfo(@RequestParam String userEmail, @RequestParam(required = false) String newPw, @AuthenticationPrincipal User user, RedirectAttributes rttr) {
        String userId = user.getUsername();
        userMapper.updateEmail(userId, userEmail);
        if (newPw != null && !newPw.isEmpty()) {
            userMapper.updatePassword(userId, passwordEncoder.encode(newPw));
        }
        rttr.addFlashAttribute("msg", "회원 정보가 성공적으로 수정되었습니다.");
        return "redirect:/mypage";
    }

    @GetMapping("/withdraw")
    public String withdrawPage() { return "mypage/withdraw"; }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam String userPw, @AuthenticationPrincipal User user, HttpSession session, RedirectAttributes rttr) {
        if (user == null) return "redirect:/login";
        String userId = user.getUsername();
        MemberDTO member = userMapper.findByUserId(userId);
        if (member != null && passwordEncoder.matches(userPw, member.getUserPw())) {
            userMapper.deleteUser(userId);
            session.invalidate();
            rttr.addFlashAttribute("msg", "회원 탈퇴가 완료되었습니다.");
            return "redirect:/";
        } else {
            rttr.addFlashAttribute("msg", "비밀번호가 일치하지 않습니다.");
            return "redirect:/withdraw";
        }
    }

    @GetMapping("/support")
    public String supportPage() { return "support"; }
}