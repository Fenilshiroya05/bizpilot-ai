package com.bizpilot.identity;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.identity.entity.Permission;
import com.bizpilot.identity.entity.Role;
import com.bizpilot.identity.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Flyway-seeded RBAC catalog (V4, extended by V7 in Phase 9)
 * matches the documented mapping in docs/security.md exactly — role
 * permissions section "Verify each configured role receives the intended
 * permissions."
 *
 * <p>Updated in Phase 9: V7 added {@code PRODUCT_READ}/{@code CREATE}/
 * {@code UPDATE}/{@code DELETE} to the catalog (CLAUDE.md §9 frames its
 * permission list as "Examples:", not a closed set — see V7's migration
 * comment and docs/security.md). This is an expected, necessary update to
 * a pre-existing Phase 6 regression test, not a change to its purpose:
 * updating the fixed expected-permission-set literals to match the now
 * intentionally-larger catalog, the same way Phase 6 itself updated Phase
 * 4/5 test literals when the JWT claim shape changed.
 *
 * <p>{@code @Transactional} keeps the Hibernate session open for the lazy
 * {@code Role.permissions} collection — safe here because, unlike
 * {@code RbacAuthorizationTests}, this class makes no HTTP calls that would
 * need to observe the same data through a separate, uncommitted connection.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class RoleSeedDataTests {

    private static final Set<String> ALL_PERMISSIONS = Set.of(
            "CUSTOMER_READ", "CUSTOMER_CREATE", "CUSTOMER_UPDATE", "CUSTOMER_DELETE",
            "LEAD_READ", "LEAD_CREATE", "LEAD_UPDATE", "LEAD_DELETE",
            "QUOTATION_READ", "QUOTATION_CREATE", "QUOTATION_UPDATE", "QUOTATION_DELETE",
            "DOCUMENT_READ", "DOCUMENT_UPLOAD", "AI_USE", "USER_MANAGE",
            "PRODUCT_READ", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE"
    );

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void allFiveRolesFromClaudeMdExistAndNoOthers() {
        Set<String> roleNames = roleRepository.findAll().stream().map(Role::getName).collect(Collectors.toSet());

        assertThat(roleNames).containsExactlyInAnyOrder("OWNER", "ADMIN", "MANAGER", "SALES", "EMPLOYEE");
    }

    @Test
    void thePermissionCatalogFromClaudeMdExistsAndNoOthers() {
        Role anyRole = roleRepository.findByName("OWNER").orElseThrow();
        // OWNER has the full catalog (see next test) — use it just to confirm the
        // catalog size/content without a separate PermissionRepository dependency.
        Set<String> permissionNames = anyRole.getPermissions().stream()
                .map(Permission::getName).collect(Collectors.toSet());

        assertThat(permissionNames).isEqualTo(ALL_PERMISSIONS);
    }

    @Test
    void ownerAndAdminHaveTheFullPermissionCatalog() {
        for (String roleName : Set.of("OWNER", "ADMIN")) {
            Role role = roleRepository.findByName(roleName).orElseThrow();
            Set<String> permissions = role.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
            assertThat(permissions).as("permissions for " + roleName).isEqualTo(ALL_PERMISSIONS);
        }
    }

    @Test
    void managerHasFullCrudButNotUserManage() {
        Role manager = roleRepository.findByName("MANAGER").orElseThrow();
        Set<String> permissions = manager.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());

        assertThat(permissions).containsExactlyInAnyOrder(
                "CUSTOMER_READ", "CUSTOMER_CREATE", "CUSTOMER_UPDATE", "CUSTOMER_DELETE",
                "LEAD_READ", "LEAD_CREATE", "LEAD_UPDATE", "LEAD_DELETE",
                "QUOTATION_READ", "QUOTATION_CREATE", "QUOTATION_UPDATE", "QUOTATION_DELETE",
                "DOCUMENT_READ", "DOCUMENT_UPLOAD", "AI_USE",
                "PRODUCT_READ", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE");
    }

    @Test
    void salesHasCrudWithoutDeleteOrUserManage() {
        Role sales = roleRepository.findByName("SALES").orElseThrow();
        Set<String> permissions = sales.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());

        assertThat(permissions).containsExactlyInAnyOrder(
                "CUSTOMER_READ", "CUSTOMER_CREATE", "CUSTOMER_UPDATE",
                "LEAD_READ", "LEAD_CREATE", "LEAD_UPDATE",
                "QUOTATION_READ", "QUOTATION_CREATE", "QUOTATION_UPDATE",
                "DOCUMENT_READ", "DOCUMENT_UPLOAD", "AI_USE",
                "PRODUCT_READ", "PRODUCT_CREATE", "PRODUCT_UPDATE");
        assertThat(permissions).doesNotContain(
                "CUSTOMER_DELETE", "LEAD_DELETE", "QUOTATION_DELETE", "PRODUCT_DELETE", "USER_MANAGE");
    }

    @Test
    void employeeHasReadOnlyPermissionsPlusAiUse() {
        Role employee = roleRepository.findByName("EMPLOYEE").orElseThrow();
        Set<String> permissions = employee.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());

        assertThat(permissions).containsExactlyInAnyOrder(
                "CUSTOMER_READ", "LEAD_READ", "QUOTATION_READ", "DOCUMENT_READ", "AI_USE", "PRODUCT_READ");
    }

    @Test
    void onlyOwnerAndAdminHaveUserManage() {
        for (String roleName : Set.of("OWNER", "ADMIN")) {
            Role role = roleRepository.findByName(roleName).orElseThrow();
            assertThat(role.getPermissions()).extracting(Permission::getName).contains("USER_MANAGE");
        }
        for (String roleName : Set.of("MANAGER", "SALES", "EMPLOYEE")) {
            Role role = roleRepository.findByName(roleName).orElseThrow();
            assertThat(role.getPermissions()).extracting(Permission::getName).doesNotContain("USER_MANAGE");
        }
    }
}
