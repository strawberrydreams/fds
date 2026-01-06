package min.boot.project.service;

import min.boot.project.mapper.UserMapper;
import min.boot.project.thymeleaf.MemberDTO;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PrincipalDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public PrincipalDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        MemberDTO user = userMapper.findByUserId(username);

        if (user == null) {
            System.out.println(">>> [로그인실패] DB에 아이디가 없음: " + username);
            throw new UsernameNotFoundException(username);
        }

        // 이 부분이 null로 나오면 1번의 매핑 문제입니다.
        System.out.println(">>> [DB 조회 결과] 아이디: " + user.getUserId());
        System.out.println(">>> [DB 조회 결과] 암호화된 비번: " + user.getUserPw());

        return User.builder()
                .username(user.getUserId())
                .password(user.getUserPw())
                .roles("USER")
                .build();
    }
}