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
        // 1. DB에서 사용자 정보 가져오기
        MemberDTO member = userMapper.findByUserId(username);

        if (member == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username);
        }

        // 2. 권한 부여 부분 (질문하신 코드 위치)
        List<GrantedAuthority> authorities = new ArrayList<>();

        // 만약 DB에 ROLE 컬럼을 추가했다면 member.getRole()을 사용하면 되고,
        // 현재는 아이디가 admin인 경우만 특수하게 처리한다면 아래처럼 합니다.
        if ("admin".equals(member.getUserId())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        // 3. 스프링 시큐리티의 User 객체 반환
        return new User(member.getUserId(), member.getUserPw(), authorities);
    }
}