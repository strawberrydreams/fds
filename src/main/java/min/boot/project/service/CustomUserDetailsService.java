package min.boot.project.service; // 본인의 패키지 경로에 맞게 수정

import min.boot.project.mapper.UserMapper;
import min.boot.project.thymeleaf.MemberDTO;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public CustomUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        MemberDTO member = userMapper.findByUserId(username);

        if (member == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username);
        }

        // 권한 리스트 생성
        List<GrantedAuthority> authorities = new ArrayList<>();

        String role = member.getRole();
        if (role != null) {
            // DB에 "ROLE_"이 이미 붙어있든 아니든, 최종적으로 "ROLE_ADMIN" 형태가 되도록 보정
            if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role;
            }
            authorities.add(new SimpleGrantedAuthority(role));
        }

        return User.builder()
                .username(member.getUserId())
                .password(member.getUserPw())
                .authorities(authorities) // .roles() 대신 .authorities() 사용
                .build();
    }
}