package net.pointofviews.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@TestConfiguration
public class FlywayConfig {
    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainerInitializer.MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainerInitializer.MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", TestContainerInitializer.MYSQL_CONTAINER::getPassword);
    }
}
