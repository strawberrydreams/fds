package min.boot.project.thymeleaf;
import lombok.Data;

@Data
public class MemberDTO {
    private String userId;    // USER_ID
    private String name;      // NAME
    private String userPw;    // USER_PW
    private String userEmail; // USER_EMAIL
    private String gender;    // GENDER
    private String birth;     // BIRTH (HTML input type="date"는 String으로 받아 변환하는 것이 편합니다)
}