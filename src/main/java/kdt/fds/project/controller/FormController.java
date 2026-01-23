package kdt.fds.project.controller;

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

    @GetMapping("/support")
    public String supportPage() {
        return "support/support";
    }

    @GetMapping("/mypage")
    public String myPage(@AuthenticationPrincipal User user, Model model) {
        if (user == null) return "redirect:/login";

        String loginId = user.getUsername();
        log.info("=== 마이페이지 데이터 로딩 시작: {} ===", loginId);

        try {
            model.addAttribute("member", userMapper.findByUserId(loginId));
            List<Account> accounts = accountService.getAccountsByLoginId(loginId);
            model.addAttribute("totalBalance", accounts != null ? accounts.stream().mapToLong(Account::getBalance).sum() : 0);
            model.addAttribute("accountCount", accounts != null ? accounts.size() : 0);

            List<Card> myCards = cardService.getCardsByLoginId(loginId);
            model.addAttribute("cards", myCards);
            model.addAttribute("cardCount", myCards != null ? myCards.size() : 0);

            List<CombinedTransactionDTO> combinedList = new ArrayList<>();
            List<Transaction> accountHistories = accountService.getRecentTransactionsByLoginId(loginId);
            if (accountHistories != null) {
                for (Transaction tx : accountHistories) {
                    combinedList.add(new CombinedTransactionDTO(tx.getCreatedAt(), tx.getDescription(), tx.getAmount(), "계좌", "SUCCESS"));
                }
            }

            List<CardTransaction> cardHistories = cardService.getCardTransactionsByLoginId(loginId);
            if (cardHistories != null) {
                for (CardTransaction ctx : cardHistories) {
                    combinedList.add(new CombinedTransactionDTO(ctx.getApprovedAt(), ctx.getMerchantName(), ctx.getAmount(), "카드", ctx.getStatus()));
                }
            }

            if (!combinedList.isEmpty()) {
                List<CombinedTransactionDTO> recentTransactions = combinedList.stream()
                        .filter(tx -> tx.getTransactionDate() != null)
                        .sorted(Comparator.comparing(CombinedTransactionDTO::getTransactionDate).reversed())
                        .limit(5)
                        .collect(Collectors.toList());
                model.addAttribute("recentTransactions", recentTransactions);
            } else {
                model.addAttribute("recentTransactions", new ArrayList<>());
            }

        } catch (Exception e) {
            log.error("마이페이지 에러 발생: ", e);
        }

        return "mypage/mypage";
    }

    @GetMapping("/login")
    public String loginForm() { return "login/login"; }

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

    /**
     * [추가] 405 에러 해결: 본인 확인 비밀번호 검증 처리
     */
    @PostMapping("/mypage/confirm")
    public String processConfirm(@AuthenticationPrincipal User user, @RequestParam("currentPw") String currentPw, Model model) {
        MemberDTO member = userMapper.findByUserId(user.getUsername());
        if (passwordEncoder.matches(currentPw, member.getUserPw())) {
            return "redirect:/mypage/edit";
        } else {
            model.addAttribute("msg", "비밀번호가 일치하지 않습니다.");
            return "mypage/mypage_confirm";
        }
    }

    @GetMapping("/mypage/edit")
    public String editPage(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("member", userMapper.findByUserId(user.getUsername()));
        return "mypage/mypage_edit";
    }

    /**
     * [추가] 회원 정보 수정 처리
     */
    @PostMapping("/mypage/update")
    public String updateMember(@AuthenticationPrincipal User user,
                               @RequestParam("userEmail") String userEmail,
                               @RequestParam(value = "newPw", required = false) String newPw,
                               Model model) {
        MemberDTO member = userMapper.findByUserId(user.getUsername());
        member.setUserEmail(userEmail);

        if (newPw != null && !newPw.isEmpty()) {
            member.setUserPw(passwordEncoder.encode(newPw));
        }

        userMapper.update(member);
        model.addAttribute("msg", "정보 수정이 완료되었습니다.");
        model.addAttribute("member", member);
        return "mypage/mypage_edit";
    }

    @GetMapping("/withdraw")
    public String withdrawPage() { return "mypage/withdraw"; }

    /**
     * [추가] 회원 탈퇴 처리
     */
    @PostMapping("/withdraw")
    public String processWithdraw(@AuthenticationPrincipal User user, @RequestParam("userPw") String userPw, Model model) {
        MemberDTO member = userMapper.findByUserId(user.getUsername());
        if (passwordEncoder.matches(userPw, member.getUserPw())) {
            userMapper.deleteByUserId(user.getUsername());
            return "redirect:/logout"; // 탈퇴 후 로그아웃 처리
        } else {
            model.addAttribute("error", "비밀번호가 일치하지 않습니다.");
            return "mypage/withdraw";
        }
    }
}