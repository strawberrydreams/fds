package kdt.fds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
// kdt 패키지 하위의 모든 것을 강제로 스캔하도록 지정합니다.
@ComponentScan(basePackages = {"kdt"})
public class FdsApplication {
    public static void main(String[] args) {
        SpringApplication.run(FdsApplication.class, args);
    }
}