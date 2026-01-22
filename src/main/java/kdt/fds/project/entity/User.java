package kdt.fds.project.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "USERS")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_USERS")
    @SequenceGenerator(name = "SEQ_USERS", sequenceName = "SEQ_USERS", allocationSize = 1)
    private Long id;

    @Column(name = "USER_ID", unique = true, nullable = false, length = 50)
    private String userId;   // 로그인 아이디

    @Column(name = "USER_PW", nullable = false)
    private String userPw;   // 비밀번호

    @Column(name = "NAME", nullable = false, length = 50)
    private String name;

    @Column(name = "USER_EMAIL", length = 100)
    private String userEmail;

    @Column(name = "GENDER", length = 10)
    private String gender;

    @Column(name = "BIRTH", length = 20)
    private String birth;
    
    @Column(name = "ROLE", length = 20)
    private String role;     // 권한 (USER, ADMIN)

    @Column(name = "PW_QUESTION", length = 255)
    private String pwQuestion; // 비밀번호 찾기 질문

    @Column(name = "PW_ANSWER", length = 255)
    private String pwAnswer;   // 비밀번호 찾기 답변

    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Account> accounts = new ArrayList<>();
}