package com.bizpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration is excluded: authentication is entirely
// JWT-bearer based (see security package) — there is no UserDetailsService,
// HTTP Basic, or form login, so Spring Boot's default generated in-memory
// user/password (otherwise logged at startup) would be unused dead weight.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BizPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(BizPilotApplication.class, args);
    }
}
