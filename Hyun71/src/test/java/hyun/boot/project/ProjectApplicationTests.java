package hyun.boot.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;

@SpringBootTest

@ActiveProfiles("oracle")
class ProjectApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Test
    void test() throws Exception {
        try(Connection conn = dataSource.getConnection()) {
            System.out.println("URL  = " + conn.getMetaData().getURL());
            System.out.println("User =" + conn.getMetaData().getUserName());
        }
    }
//    void contextLoads() {
//    }

}
