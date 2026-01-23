package kdt.fds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// kdt 패키지 하위의 모든 것을 강제로 스캔하도록 지정합니다.
@ComponentScan(basePackages = "kdt")
@EnableScheduling // 스냅샷 생성 기능 자동화 스케줄링 활성화 (매주 월요일 00:00 서울특별시 UTC+9)
public class FdsApplication {
    public static void main(String[] args) {
        SpringApplication.run(FdsApplication.class, args);
    }
}
