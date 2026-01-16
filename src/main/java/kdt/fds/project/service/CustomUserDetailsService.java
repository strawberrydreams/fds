package kdt.fds.project.service; // 본인의 패키지 경로에 맞게 수정

import kdt.fds.project.mapper.UserMapper;
import kdt.fds.project.controller.MemberDTO;
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

        // DB에 저장된 ROLE 값을 읽어와서 권한 부여
        // .roles() 메서드는 자동으로 "ROLE_"을 붙여줍니다 (ADMIN -> ROLE_ADMIN)
        return User.builder()
                .username(member.getUserId())
                .password(member.getUserPw())
                .roles(member.getRole())
                .build();
    }
}