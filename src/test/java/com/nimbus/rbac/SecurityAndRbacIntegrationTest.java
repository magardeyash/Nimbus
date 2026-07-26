package com.nimbus.rbac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbus.identity.User;
import com.nimbus.identity.UserRepository;
import com.nimbus.identity.JwtService;
import com.nimbus.tenant.Company;
import com.nimbus.tenant.CompanyRepository;
import com.nimbus.tenant.TenantContext;
import com.nimbus.workspace.Workspace;
import com.nimbus.workspace.WorkspaceMembership;
import com.nimbus.workspace.WorkspaceMembershipRepository;
import com.nimbus.workspace.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAndRbacIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMembershipRepository workspaceMembershipRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private JwtService jwtService;

    private User userA;
    private User userB;

    private UUID workspaceAId;
    private UUID workspaceBId;

    private Company companyA;
    private Company companyB;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        workspaceMembershipRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        // 1. Seed global Permissions
        Permission readPermission = permissionRepository.findById("task:read")
                .orElseGet(() -> permissionRepository.save(Permission.builder().code("task:read").description("Read tasks").build()));
        Permission createPermission = permissionRepository.findById("task:create")
                .orElseGet(() -> permissionRepository.save(Permission.builder().code("task:create").description("Create tasks").build()));

        // 2. Seed Companies (Tenants)
        companyA = Company.builder().id(UUID.randomUUID()).name("Company A").slug("company-a").build();
        companyB = Company.builder().id(UUID.randomUUID()).name("Company B").slug("company-b").build();
        companyRepository.saveAll(List.of(companyA, companyB));

        // 3. Seed Users
        userA = User.builder().email("usera@company.com").fullName("User A").enabled(true).build();
        userB = User.builder().email("userb@company.com").fullName("User B").enabled(true).build();
        userRepository.saveAll(List.of(userA, userB));

        tokenA = jwtService.generateAccessToken(userA);
        tokenB = jwtService.generateAccessToken(userB);

        // 4. Seed Roles (Tenant scoped)
        Role viewerRole = Role.builder()
                .id(UUID.randomUUID())
                .tenantId(companyA.getId())
                .name("Viewer")
                .permissions(Set.of(readPermission))
                .build();

        Role adminRole = Role.builder()
                .id(UUID.randomUUID())
                .tenantId(companyB.getId())
                .name("Admin")
                .permissions(Set.of(readPermission, createPermission))
                .build();
        roleRepository.saveAll(List.of(viewerRole, adminRole));

        // 5. Seed Workspaces (RLS must be bypassed or context set to seed workspaces)
        // Since workspaces table has RLS policy: tenant_id = current_tenant_id, we set TenantContext before seeding
        TenantContext.setTenantId(companyA.getId());
        workspaceAId = UUID.randomUUID();
        Workspace wsA = Workspace.builder().id(workspaceAId).tenantId(companyA.getId()).name("Workspace A").slug("ws-a").build();
        workspaceRepository.save(wsA);

        TenantContext.setTenantId(companyB.getId());
        workspaceBId = UUID.randomUUID();
        Workspace wsB = Workspace.builder().id(workspaceBId).tenantId(companyB.getId()).name("Workspace B").slug("ws-b").build();
        workspaceRepository.save(wsB);

        TenantContext.clear();

        // 6. Seed Workspace Memberships
        TenantContext.setTenantId(companyA.getId());
        WorkspaceMembership memberA = WorkspaceMembership.builder()
                .tenantId(companyA.getId())
                .workspaceId(workspaceAId)
                .userId(userA.getId())
                .roleId(viewerRole.getId())
                .build();
        workspaceMembershipRepository.save(memberA);

        TenantContext.setTenantId(companyB.getId());
        WorkspaceMembership memberB = WorkspaceMembership.builder()
                .tenantId(companyB.getId())
                .workspaceId(workspaceBId)
                .userId(userB.getId())
                .roleId(adminRole.getId())
                .build();
        workspaceMembershipRepository.save(memberB);

        TenantContext.clear();
    }

    @Test
    void shouldDenyUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceAId + "/test-read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldBlockAccessIfUserIsNotAMemberOfWorkspace() throws Exception {
        // User B attempts to access Workspace A (no membership)
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceAId + "/test-read")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden()); // Thrown by TenantResolutionInterceptor
    }

    @Test
    void shouldAllowReadIfUserIsViewerAndHasReadPermission() throws Exception {
        // User A is a member of Workspace A with Viewer role (has task:read)
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceAId + "/test-read")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(content().string("Read Success. Tenant: " + companyA.getId())); // Verification output

        // Verify that TenantContext is cleared after the request finishes
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void shouldDenyWriteIfUserIsViewerLackingCreatePermission() throws Exception {
        // User A attempts to write in Workspace A (lacks task:create)
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceAId + "/test-write")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden()); // Denied by CustomPermissionEvaluator
    }

    @Test
    void shouldAllowWriteIfUserIsAdminWithCreatePermission() throws Exception {
        // User B is an Admin in Workspace B (has task:create)
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceBId + "/test-write")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(content().string("Write Success. Tenant: " + companyB.getId()));

        assertThat(TenantContext.getTenantId()).isNull();
    }
}
