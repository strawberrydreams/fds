package kdt.fds.common.service;

import kdt.fds.user.mapper.UserMapper;
import kdt.fds.user.dto.MemberDTO;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

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

        // [복구] .roles()는 DB의 'ADMIN' 앞에 'ROLE_'을 자동으로 붙여 ROLE_ADMIN으로 만듭니다.
        // 이 과정이 있어야 시큐리티가 ADMIN 계정을 인식하고 권한을 부여합니다.
        return User.builder()
                .username(member.getUserId())
                .password(member.getUserPw())
                .roles(member.getRole())
                .build();
    }
}