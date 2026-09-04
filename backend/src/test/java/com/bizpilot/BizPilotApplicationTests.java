package com.bizpilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BizPilotApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring application context starts successfully with
        // the current configuration and package structure.
    }
}
