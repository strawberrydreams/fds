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

import jakarta.servlet.http.HttpSession;

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
    public String submitForm(@ModelAttribute MemberDTO memberDTO) {
        // 암호화 및 질문/답변/생년월일 포함 저장
        memberDTO.setUserPw(passwordEncoder.encode(memberDTO.getUserPw()));
        userMapper.save(memberDTO);
        return "thymeleaf/join_result";
    }

    // [3] 마이페이지 대시보드
    @GetMapping("/mypage")
    public String myPage(@AuthenticationPrincipal User user, Model model) {
        if (user == null) return "redirect:/login";
        model.addAttribute("member", userMapper.findByUserId(user.getUsername()));
        return "thymeleaf/mypage";
    }

    // [4] 정보 수정 - 본인 확인 페이지
    @GetMapping("/mypage/confirm")
    public String confirmPage() { return "thymeleaf/mypage_confirm"; }

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

    // [5] 정보 수정 - 수정 폼
    @GetMapping("/mypage/edit")
    public String editPage(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("member", userMapper.findByUserId(user.getUsername()));
        return "thymeleaf/mypage_edit";
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

    // [6] 계정 찾기 서비스
    @GetMapping("/find-account")
    public String findAccountPage() { return "thymeleaf/find_account"; }

    // 아이디 찾기 (이름, 이메일 + 생년월일 추가)
    @PostMapping("/find-id")
    public String findId(@RequestParam String name, @RequestParam String userEmail, @RequestParam String birth, RedirectAttributes rttr) {
        String foundId = userMapper.findIdByNameAndEmailAndBirth(name, userEmail, birth);
        rttr.addFlashAttribute("msg", foundId != null ? "아이디: [" + foundId + "]" : "일치하는 정보가 없습니다.");
        return "redirect:/find-account";
    }

    // 비밀번호 재설정 (질문, 답변 + 생년월일 추가)
    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam(required = false) String userId,
                                @RequestParam(required = false) String userEmail,
                                @RequestParam(required = false) String birth,
                                @RequestParam(required = false) String pwQuestion,
                                @RequestParam(required = false) String pwAnswer,
                                @RequestParam String newPw,
                                Authentication auth,
                                RedirectAttributes rttr) {
        String targetUserId;

        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            targetUserId = auth.getName();
        } else {
            if (userId == null || userEmail == null || birth == null || pwQuestion == null || pwAnswer == null) {
                rttr.addFlashAttribute("msg", "모든 정보를 정확히 입력해주세요.");
                return "redirect:/find-account";
            }
            if (userMapper.checkUserExists(userId, userEmail, birth, pwQuestion, pwAnswer) == 0) {
                rttr.addFlashAttribute("msg", "본인 확인 정보가 일치하지 않습니다.");
                return "redirect:/find-account";
            }
            targetUserId = userId;
        }

        userMapper.updatePassword(targetUserId, passwordEncoder.encode(newPw));
        rttr.addFlashAttribute("msg", "비밀번호가 성공적으로 변경되었습니다.");
        return (auth != null && auth.isAuthenticated()) ? "redirect:/mypage" : "redirect:/login";
    }

    // [7] 비동기 본인 확인 (JS Fetch용)
    @GetMapping("/check-user")
    @ResponseBody
    public boolean checkUser(@RequestParam String userId,
                             @RequestParam String userEmail,
                             @RequestParam String birth,
                             @RequestParam String pwQuestion,
                             @RequestParam String pwAnswer) {
        return userMapper.checkUserExists(userId, userEmail, birth, pwQuestion, pwAnswer) > 0;
    }

    // [8] 회원 탈퇴 페이지 이동
    @GetMapping("/withdraw")
    public String withdrawPage() {
        return "thymeleaf/withdraw";
    }

    // [9] 회원 탈퇴 처리
    @PostMapping("/withdraw")
    public String withdraw(@RequestParam String userPw,
                           @AuthenticationPrincipal User user,
                           HttpSession session,
                           RedirectAttributes rttr) {

        if (user == null) return "redirect:/login";

        String userId = user.getUsername();
        MemberDTO member = userMapper.findByUserId(userId);

        if (member != null && passwordEncoder.matches(userPw, member.getUserPw())) {
            userMapper.deleteUser(userId); // DB 데이터 삭제
            session.invalidate(); // 세션 무효화 및 로그아웃 처리

            rttr.addFlashAttribute("msg", "회원 탈퇴가 완료되었습니다. 이용해 주셔서 감사합니다.");
            return "redirect:/";
        } else {
            rttr.addFlashAttribute("msg", "비밀번호가 일치하지 않습니다.");
            return "redirect:/withdraw";
        }
    }

    @GetMapping("/support")
    public String supportPage() { return "thymeleaf/support"; }
}