package kdt.fds.project.controller;

import kdt.fds.project.mapper.UserMapper;
import kdt.fds.project.entity.Account;
import kdt.fds.project.entity.Card;
import kdt.fds.project.entity.Transaction;
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
import java.util.List;

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
        log.info("마이페이지 통합 거래내역 조회 시작 - ID: {}", loginId);

        try {
            model.addAttribute("member", userMapper.findByUserId(loginId));
            List<Account> accounts = accountService.getAccountsByLoginId(loginId);
            if (accounts == null) accounts = new ArrayList<>();
            model.addAttribute("totalBalance", accounts.stream().mapToLong(Account::getBalance).sum());
            model.addAttribute("accountCount", accounts.size());

            List<Card> myCards = cardService.getCardsByLoginId(loginId);
            model.addAttribute("cards", myCards);
            model.addAttribute("cardCount", myCards != null ? myCards.size() : 0);

            List<Transaction> accountHistories = accountService.getRecentTransactionsByLoginId(loginId);
            if (accountHistories == null) accountHistories = new ArrayList<>();
            model.addAttribute("accountHistories", accountHistories);
        } catch (Exception e) {
            log.error("마이페이지 로딩 중 오류 발생: ", e);
        }
        return "mypage/mypage";
    }

    @GetMapping("/login")
    public String loginForm() { return "login/login"; }

    // [복구] 아이디/비밀번호 찾기 페이지 매핑
    @GetMapping("/find-account")
    public String findAccountPage() {
        return "login/find_account"; // templates/login/find_account.html 호출
    }

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