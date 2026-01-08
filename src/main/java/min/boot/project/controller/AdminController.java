package min.boot.project.controller;

import min.boot.project.mapper.UserMapper;
import min.boot.project.thymeleaf.MemberDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    // [1] 전체 회원 리스트
    @GetMapping("/users")
    public String userList(Model model) {
        List<MemberDTO> users = userMapper.findAllUsers();
        model.addAttribute("users", users);
        return "thymeleaf/admin/user_list";
    }

    // [2] 회원 수정 폼 (관리자용)
    @GetMapping("/users/edit/{userId}")
    public String editUserForm(@PathVariable String userId, Model model) {
        MemberDTO member = userMapper.findByUserId(userId);
        model.addAttribute("member", member);
        return "thymeleaf/admin/user_edit";
    }

    // [3] 회원 정보 강제 업데이트 처리
    @PostMapping("/users/update")
    public String updateUser(@ModelAttribute MemberDTO memberDTO,
                             @RequestParam(required = false) String newPw,
                             RedirectAttributes rttr) {

        // 비밀번호 강제 변경 요청이 있을 경우 처리
        if (newPw != null && !newPw.isEmpty()) {
            memberDTO.setUserPw(passwordEncoder.encode(newPw));
            userMapper.updatePassword(memberDTO.getUserId(), memberDTO.getUserPw());
        }

        // 나머지 정보(이름, 이메일, 생년월일, 성별, 권한) 강제 업데이트
        userMapper.updateUserByAdmin(memberDTO);

        rttr.addFlashAttribute("msg", "회원 정보가 관리자에 의해 강제 수정되었습니다.");
        return "redirect:/admin/users";
    }

    // [4] 회원 강제 삭제(탈퇴)
    @PostMapping("/users/delete/{userId}")
    public String deleteUser(@PathVariable String userId, RedirectAttributes rttr) {
        userMapper.deleteUser(userId);
        rttr.addFlashAttribute("msg", "해당 회원이 강제 탈퇴 처리되었습니다.");
        return "redirect:/admin/users";
    }
}