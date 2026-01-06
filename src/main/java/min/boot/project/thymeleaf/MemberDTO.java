package min.boot.project.thymeleaf;
import lombok.Data;

@Data
public class MemberDTO {
    private String userId;
    private String name;
    private String userPw;
    private String userEmail;
    private String gender;
    private String birth;
    private String role; // 추가: 'USER' 또는 'ADMIN' 저장
}