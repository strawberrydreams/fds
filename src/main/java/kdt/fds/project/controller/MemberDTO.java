package kdt.fds.project.controller;
import lombok.Data;

@Data
public class MemberDTO {
    private String userId;
    private String name;
    private String userPw;
    private String userEmail;
    private String gender;
    private String birth;
    private String role; // 'USER' 또는 'ADMIN' 저장됨
    private String pwQuestion;
    private String pwAnswer;
}