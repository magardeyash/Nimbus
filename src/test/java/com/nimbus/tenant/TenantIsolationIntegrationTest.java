package com.nimbus.tenant;

import com.nimbus.NimbusApplication;
import com.nimbus.workspace.Workspace;
import com.nimbus.workspace.WorkspaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NimbusApplication.class)
@Testcontainers
class TenantIsolationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("nimbus_db")
                    .withUsername("nimbus")
                    .withPassword("n1234");

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private WorkspaceService workspaceService;

    private UUID tenantAId;
    private UUID tenantBId;

    @BeforeEach
    void setUp() {
        workspaceService.deleteAllWorkspaces();
        companyRepository.deleteAll();

        // Create global tenants (companies)
        tenantAId = UUID.randomUUID();
        Company tenantA = Company.builder()
                .id(tenantAId)
                .name("Tenant A")
                .slug("tenant-a")
                .build();

        tenantBId = UUID.randomUUID();
        Company tenantB = Company.builder()
                .id(tenantBId)
                .name("Tenant B")
                .slug("tenant-b")
                .build();

        companyRepository.saveAll(List.of(tenantA, tenantB));

        // Create workspace under Tenant A
        TenantContext.setTenantId(tenantAId);
        Workspace wsA = Workspace.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantAId)
                .name("Workspace A")
                .slug("ws-a")
                .build();
        workspaceService.createWorkspace(wsA);

        // Create workspace under Tenant B
        TenantContext.setTenantId(tenantBId);
        Workspace wsB = Workspace.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantBId)
                .name("Workspace B")
                .slug("ws-b")
                .build();
        workspaceService.createWorkspace(wsB);

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        workspaceService.deleteAllWorkspaces();
        companyRepository.deleteAll();
    }

    @Test
    void shouldOnlyFetchWorkspacesBelongingToActiveTenant() {
        // Given Tenant A context is set
        TenantContext.setTenantId(tenantAId);

        // When retrieving workspaces
        List<Workspace> workspaces = workspaceService.getAllWorkspaces();

        // Then only Tenant A's workspace should be returned
        assertThat(workspaces)
                .hasSize(1)
                .allSatisfy(ws -> assertThat(ws.getTenantId()).isEqualTo(tenantAId))
                .extracting(Workspace::getName)
                .containsExactly("Workspace A");

        // Given Tenant B context is set
        TenantContext.setTenantId(tenantBId);

        // When retrieving workspaces
        workspaces = workspaceService.getAllWorkspaces();

        // Then only Tenant B's workspace should be returned
        assertThat(workspaces)
                .hasSize(1)
                .allSatisfy(ws -> assertThat(ws.getTenantId()).isEqualTo(tenantBId))
                .extracting(Workspace::getName)
                .containsExactly("Workspace B");
    }

    @Test
    void shouldReturnEmptyListWhenNoTenantContextIsSet() {
        // Given no tenant context is set
        TenantContext.clear();

        // When retrieving workspaces
        List<Workspace> workspaces = workspaceService.getAllWorkspaces();

        // Then no workspaces should be returned (blocked by RLS)
        assertThat(workspaces).isEmpty();
    }
}
