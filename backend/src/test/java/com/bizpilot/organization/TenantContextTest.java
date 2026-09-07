package com.bizpilot.organization;

import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.security.CurrentUserProvider;
import com.bizpilot.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantContextTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Test
    void resolvesTheOrganizationIdFromTheAuthenticatedPrincipal() {
        UUID organizationId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), UserRole.EMPLOYEE, organizationId);
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(principal));

        TenantContext tenantContext = new TenantContext(currentUserProvider);

        assertThat(tenantContext.currentOrganizationId()).isEqualTo(organizationId);
    }

    @Test
    void throwsWhenThereIsNoAuthenticatedPrincipal() {
        when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());

        TenantContext tenantContext = new TenantContext(currentUserProvider);

        assertThatThrownBy(tenantContext::currentOrganizationId)
                .isInstanceOf(IllegalStateException.class);
    }
}
