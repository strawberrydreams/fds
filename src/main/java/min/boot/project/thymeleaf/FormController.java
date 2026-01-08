package min.boot.project.thymeleaf;

import min.boot.project.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class FormController {

    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    // [1] 메인 페이지
    @GetMapping("/")
    public String index(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getName().equals("anonymousUser")) {
            model.addAttribute("loginUser", authentication.getName());
        }
        return "thymeleaf/main";
    }

    // [2] 로그인/회원가입
    @GetMapping("/login")
    public String loginForm() { return "thymeleaf/login"; }

    @GetMapping("/join")
    public String showForm(Model model) {
        model.addAttribute("memberDTO", new MemberDTO());
        return "thymeleaf/join";
    }

    @PostMapping("/join")
    public String submitForm(@ModelAttribute MemberDTO memberDTO, Model model) {
        memberDTO.setUserPw(passwordEncoder.encode(memberDTO.getUserPw()));
        userMapper.save(memberDTO);
        model.addAttribute("memberDTO", memberDTO);
        return "thymeleaf/join_result";
    }

    // [3] 마이페이지 대시보드
    @GetMapping("/mypage")
    public String myPage(@AuthenticationPrincipal User user, Model model) {
        if (user == null) return "redirect:/login";
        model.addAttribute("member", userMapper.findByUserId(user.getUsername()));
        return "thymeleaf/mypage";
    }

    // [4] 정보 수정 - Step 1: 비밀번호 재확인 페이지 (404 방지 위해 경로 명확히)
    @GetMapping("/mypage/confirm")
    public String confirmPage() {
        return "thymeleaf/mypage_confirm";
    }

    // [5] 정보 수정 - Step 2: 비밀번호 확인 처리
    @PostMapping("/mypage/confirm")
    public String confirmPassword(@RequestParam String currentPw,
                                  @AuthenticationPrincipal User user,
                                  RedirectAttributes rttr) {
        MemberDTO member = userMapper.findByUserId(user.getUsername());

        if (passwordEncoder.matches(currentPw, member.getUserPw())) {
            return "redirect:/mypage/edit";
        } else {
            rttr.addFlashAttribute("msg", "비밀번호가 일치하지 않습니다.");
            return "redirect:/mypage/confirm";
        }
    }

    // [6] 정보 수정 - Step 3: 수정 폼 페이지
    @GetMapping("/mypage/edit")
    public String editPage(@AuthenticationPrincipal User user, Model model) {
        if (user == null) return "redirect:/login";
        model.addAttribute("member", userMapper.findByUserId(user.getUsername()));
        return "thymeleaf/mypage_edit";
    }

    // [7] 정보 수정 - Step 4: 실제 업데이트 처리
    @PostMapping("/mypage/update")
    public String updateInfo(@RequestParam String userEmail,
                             @RequestParam(required = false) String newPw,
                             @AuthenticationPrincipal User user,
                             RedirectAttributes rttr) {
        String userId = user.getUsername();
        userMapper.updateEmail(userId, userEmail);

        if (newPw != null && !newPw.isEmpty()) {
            userMapper.updatePassword(userId, passwordEncoder.encode(newPw));
        }

        rttr.addFlashAttribute("msg", "회원 정보가 성공적으로 수정되었습니다.");
        return "redirect:/mypage";
    }

    // [8] 계정 찾기 서비스
    @GetMapping("/find-account")
    public String findAccountPage() { return "thymeleaf/find_account"; }

    @PostMapping("/find-id")
    public String findId(@RequestParam String name, @RequestParam String userEmail, RedirectAttributes rttr) {
        String foundId = userMapper.findIdByNameAndEmail(name, userEmail);
        rttr.addFlashAttribute("msg", foundId != null ? "아이디: [" + foundId + "]" : "정보가 없습니다.");
        return "redirect:/find-account";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam(required = false) String userId,
                                @RequestParam String newPw,
                                @RequestParam(required = false) String userEmail,
                                Authentication auth,
                                RedirectAttributes rttr) {
        String targetUserId;

        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            targetUserId = auth.getName();
        } else {
            if (userId == null || userEmail == null || userId.isEmpty() || userEmail.isEmpty()) {
                rttr.addFlashAttribute("msg", "아이디와 이메일을 정확히 입력해주세요.");
                return "redirect:/find-account";
            }
            int exists = userMapper.checkUserExists(userId, userEmail);
            if (exists == 0) {
                rttr.addFlashAttribute("msg", "정보가 일치하지 않습니다.");
                return "redirect:/find-account";
            }
            targetUserId = userId;
        }

        userMapper.updatePassword(targetUserId, passwordEncoder.encode(newPw));
        rttr.addFlashAttribute("msg", "비밀번호가 성공적으로 변경되었습니다.");

        return (auth != null && auth.isAuthenticated()) ? "redirect:/mypage" : "redirect:/login";
    }

    @GetMapping("/check-user")
    @ResponseBody
    public boolean checkUser(@RequestParam String userId, @RequestParam String userEmail) {
        return userMapper.checkUserExists(userId, userEmail) > 0;
    }

    // [9] 고객지원 (매핑 누락 방지)
    @GetMapping("/support")
    public String supportPage() {
        return "thymeleaf/support";
    }
}