package kdt.fds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// 시큐리티 자동 설정과 기본 유저 서비스 설정을 모두 꺼버립니다.
@SpringBootApplication(
        scanBasePackages = "kdt",
        exclude = {
                SecurityAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class
        }
)
public class FdsApplication {
    public static void main(String[] args) {
        SpringApplication.run(FdsApplication.class, args);
    }
}