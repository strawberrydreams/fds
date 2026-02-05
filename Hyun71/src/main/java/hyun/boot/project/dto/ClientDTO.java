package hyun.boot.project.dto;

import lombok.Data;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

@Data
public class ClientDTO {
    private Long clientNo;
    private String userId;
    private String userPw;
    private String name;
    private String email;
    private String gender;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate birth;
}