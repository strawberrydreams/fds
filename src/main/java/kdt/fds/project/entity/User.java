package kdt.fds.project.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
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
    // 오라클 환경에 맞춰 IDENTITY에서 SEQUENCE로 변경을 권장합니다.
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_USERS")
    @SequenceGenerator(name = "SEQ_USERS", sequenceName = "SEQ_USERS", allocationSize = 1)
    private Long id;

    @Column(name = "USER_ID", unique = true, nullable = false, length = 50)
    private String userId;   // 로그인 아이디

    @Column(name = "USER_PW", nullable = false)
    private String userPw;   // 비밀번호

    @Column(name = "NAME", nullable = false, length = 50) // NAME 컬럼 명시
    private String name;

    @Column(name = "USER_EMAIL", length = 100)
    private String userEmail;

    @Column(name = "GENDER", length = 10) // 컬럼명 대문자 통일 (선택)
    private String gender;

    @Column(name = "BIRTH", length = 20) // 컬럼명 대문자 통일 (선택)
    private String birth;

    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Account> accounts = new ArrayList<>();
}