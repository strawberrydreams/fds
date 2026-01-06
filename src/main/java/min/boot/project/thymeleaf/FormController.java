package min.boot.project.thymeleaf;

import min.boot.project.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FormController {

    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public FormController(PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

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

    // [3] 마이페이지 기본 대시보드
    @GetMapping("/mypage")
    public String myPage(@AuthenticationPrincipal User user, Model model) {
        if (user == null) return "redirect:/login";
        model.addAttribute("member", userMapper.findByUserId(user.getUsername()));
        return "thymeleaf/mypage";
    }

    // [4] 정보 수정 - Step 1: 비밀번호 재확인 페이지
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
            return "redirect:/mypage/edit"; // 인증 성공 시 수정 폼으로
        } else {
            rttr.addFlashAttribute("msg", "비밀번호가 일치하지 않습니다.");
            return "redirect:/mypage/confirm";
        }
    }

    // [6] 정보 수정 - Step 3: 수정 폼 페이지 (아이디, 이름 등 읽기전용)
    @GetMapping("/mypage/edit")
    public String editPage(@AuthenticationPrincipal User user, Model model) {
        if (user == null) return "redirect:/login";
        model.addAttribute("member", userMapper.findByUserId(user.getUsername()));
        return "thymeleaf/mypage_edit";
    }

    // [7] 정보 수정 - Step 4: 실제 업데이트 처리 (이메일, 비밀번호만)
    @PostMapping("/mypage/update")
    public String updateInfo(@RequestParam String userEmail,
                             @RequestParam(required = false) String newPw,
                             @AuthenticationPrincipal User user,
                             RedirectAttributes rttr) {
        String userId = user.getUsername();

        // 이메일 수정
        userMapper.updateEmail(userId, userEmail);

        // 새 비밀번호 입력 시에만 수정
        if (newPw != null && !newPw.isEmpty()) {
            userMapper.updatePassword(userId, passwordEncoder.encode(newPw));
        }

        rttr.addFlashAttribute("msg", "회원 정보가 성공적으로 수정되었습니다.");
        return "redirect:/mypage";
    }

    // [8] 계정 찾기 서비스 (아이디 찾기 / 외부 비밀번호 재설정)
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

        // [1] 로그인된 상태 (마이페이지에서 수정 중)
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            targetUserId = auth.getName();
        }
        // [2] 로그인 안 된 상태 (아이디/비번 찾기 페이지에서 수정 중)
        else {
            // 필수 값 체크
            if (userId == null || userEmail == null || userId.isEmpty() || userEmail.isEmpty()) {
                rttr.addFlashAttribute("msg", "아이디와 이메일을 정확히 입력해주세요.");
                return "redirect:/find-account";
            }

            // DB에 해당 아이디와 이메일이 매칭되는 유저가 있는지 확인
            int exists = userMapper.checkUserExists(userId, userEmail);
            if (exists == 0) {
                rttr.addFlashAttribute("msg", "정보가 일치하지 않습니다."); // 이 팝업은 뜬다고 하심
                return "redirect:/find-account";
            }
            targetUserId = userId;
        }

        // [3] 공통 처리: 위 검증을 통과해야만 여기까지 내려옵니다.
        userMapper.updatePassword(targetUserId, passwordEncoder.encode(newPw));

        // 성공 메시지 담기
        rttr.addFlashAttribute("msg", "비밀번호가 성공적으로 변경되었습니다.");

        // [4] 리다이렉트 경로 결정
        if (auth != null && auth.isAuthenticated()) {
            return "redirect:/mypage"; // 로그인 상태면 마이페이지로
        } else {
            return "redirect:/login";  // 비로그인 상태면 로그인 페이지로
        }
    }

    @GetMapping("/check-user")
    @ResponseBody // 페이지 이동이 아니라 '데이터(true/false)'만 보내기 위해 필수!
    public boolean checkUser(@RequestParam String userId, @RequestParam String userEmail) {
        // 0보다 크면 유저가 존재하므로 true 반환
        return userMapper.checkUserExists(userId, userEmail) > 0;
    }
}