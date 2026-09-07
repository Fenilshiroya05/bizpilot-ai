package com.bizpilot.organization.service;

import com.bizpilot.organization.TenantContext;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private TenantContext tenantContext;

    private OrganizationService organizationService() {
        return new OrganizationService(organizationRepository, tenantContext);
    }

    @Test
    void createTrimsTheNameAndSavesANewOrganization() {
        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        when(organizationRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Organization created = organizationService().create("  Acme Corp  ");

        assertThat(captor.getValue().getName()).isEqualTo("Acme Corp");
        assertThat(created).isSameAs(captor.getValue());
    }

    @Test
    void getCurrentOrganizationResolvesStrictlyFromTenantContextNeverFromAnExternalId() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization("Acme Corp");
        when(tenantContext.currentOrganizationId()).thenReturn(organizationId);
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));

        Organization result = organizationService().getCurrentOrganization();

        assertThat(result).isSameAs(organization);
        verify(tenantContext).currentOrganizationId();
    }

    @Test
    void getCurrentOrganizationFailsSafelyIfTheResolvedOrganizationNoLongerExists() {
        UUID organizationId = UUID.randomUUID();
        when(tenantContext.currentOrganizationId()).thenReturn(organizationId);
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService().getCurrentOrganization())
                .isInstanceOf(IllegalStateException.class);
    }
}
