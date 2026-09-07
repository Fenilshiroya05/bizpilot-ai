package com.bizpilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class BizPilotApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring application context starts successfully,
        // including a real datasource, Flyway migration, and JPA validation
        // against the containerized PostgreSQL from TestcontainersConfiguration.
    }
}
