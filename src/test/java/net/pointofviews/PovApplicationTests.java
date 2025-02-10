package net.pointofviews;

import net.pointofviews.config.TestContainerInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

@SpringBootTest
@ImportTestcontainers(TestContainerInitializer.class)
class PovApplicationTests {

    @Test
    void contextLoads() {
    }

}
