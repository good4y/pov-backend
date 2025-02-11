package net.pointofviews.config;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

public interface TestContainerInitializer {
    @Container
    @ServiceConnection
    MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.4.3")
            .withDatabaseName("pov-test")
            .withUsername("root")
            .withPassword("root1234");

    @Container
    @ServiceConnection
    GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>("redis:7.2")
            .withExposedPorts(6379);
}
