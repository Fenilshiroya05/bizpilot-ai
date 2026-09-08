package com.bizpilot.devseed;

import com.bizpilot.identity.entity.Role;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.repository.RoleRepository;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.identity.service.UserService;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.service.OrganizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 22.5 — local-development-only seed: one demo organization plus one
 * user per {@link UserRole}, so a developer can log in locally as any role
 * without a real invite/user-management flow (which doesn't exist yet — see
 * {@code UserController}'s absence and {@code UserService}'s Javadoc).
 *
 * <p><b>Never runs unless BOTH conditions hold</b>: the {@code local} Spring
 * profile is active AND {@code bizpilot.seed.demo-users=true} is explicitly
 * set (never on by default, so a plain {@code ./mvnw spring-boot:run} or a
 * CI/test run never seeds anything). This is pure local developer
 * convenience — it touches no production code path and is never active
 * outside a developer's own machine.
 *
 * <p><b>Idempotent</b>: guarded by checking whether the OWNER account
 * already exists; if so, this is a no-op. Running it twice never creates a
 * second demo organization or duplicate users.
 *
 * <p>Reuses the exact same {@link UserService#register} /
 * {@link OrganizationService#create} methods every real registration goes
 * through (same password hashing, same email-uniqueness check, same
 * organization-creation path) — the one thing it does that the public API
 * cannot do is put more than one user in the same organization with a role
 * other than {@link UserRole#EMPLOYEE}, since there is no
 * invite/add-teammate endpoint yet. That one gap is bridged here by
 * reassigning the {@link User#getRoles()} set directly after registration —
 * still the real, mutable entity relationship {@code AuthService} itself
 * reads fresh on every login, not a schema or security bypass.
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "bizpilot.seed.demo-users", havingValue = "true")
public class DemoUsersSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUsersSeeder.class);

    /** Local/demo-only — never a real domain, never committed as a real credential. */
    private static final String DEMO_ORGANIZATION_NAME = "BizPilot Demo Co";
    private static final String DEMO_PASSWORD = "Passw0rd1";

    private record DemoAccount(String email, String firstName, String lastName, UserRole role) {
    }

    private static final DemoAccount[] DEMO_ACCOUNTS = {
            new DemoAccount("owner@bizpilot.local", "Demo", "Owner", UserRole.OWNER),
            new DemoAccount("admin@bizpilot.local", "Demo", "Admin", UserRole.ADMIN),
            new DemoAccount("manager@bizpilot.local", "Demo", "Manager", UserRole.MANAGER),
            new DemoAccount("sales@bizpilot.local", "Demo", "Sales", UserRole.SALES),
            new DemoAccount("employee@bizpilot.local", "Demo", "Employee", UserRole.EMPLOYEE),
    };

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;
    private final OrganizationService organizationService;

    public DemoUsersSeeder(UserRepository userRepository, RoleRepository roleRepository, UserService userService,
                            OrganizationService organizationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userService = userService;
        this.organizationService = organizationService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByEmailIgnoreCase(DEMO_ACCOUNTS[0].email())) {
            log.info("[devseed] Demo users already exist — skipping (idempotent no-op).");
            return;
        }

        Organization organization = organizationService.create(DEMO_ORGANIZATION_NAME);
        log.info("[devseed] Created demo organization '{}' ({})", organization.getName(), organization.getId());

        for (DemoAccount account : DEMO_ACCOUNTS) {
            User user = userService.register(account.email(), DEMO_PASSWORD, account.firstName(),
                    account.lastName(), organization);
            if (account.role() != UserRole.EMPLOYEE) {
                Role targetRole = roleRepository.findByName(account.role().name())
                        .orElseThrow(() -> new IllegalStateException(
                                "RBAC seed data missing: role " + account.role().name() + " not found"));
                user.getRoles().clear();
                user.getRoles().add(targetRole);
                userRepository.save(user);
            }
            log.info("[devseed] Created demo user {} ({})", account.email(), account.role());
        }

        log.info("[devseed] Demo seeding complete — {} users in organization '{}'. "
                        + "Local-only credentials, password '{}' for every account.",
                DEMO_ACCOUNTS.length, organization.getName(), DEMO_PASSWORD);
    }
}
