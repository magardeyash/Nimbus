package com.nimbus.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbus.identity.User;
import com.nimbus.identity.UserRepository;
import com.nimbus.identity.JwtService;
import com.nimbus.tenant.Company;
import com.nimbus.tenant.CompanyRepository;
import com.nimbus.tenant.TenantContext;
import com.nimbus.rbac.Permission;
import com.nimbus.rbac.PermissionRepository;
import com.nimbus.rbac.Role;
import com.nimbus.rbac.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceAndInvitationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private InvitationRepository invitationRepository;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private JwtService jwtService;

    private Company company;
    private User userA;
    private User userB;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        workspaceMembershipRepository.deleteAll();
        invitationRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        // 1. Ensure global permissions exist in DB
        Permission invitePermission = permissionRepository.findById("member:invite")
                .orElseGet(() -> permissionRepository.save(Permission.builder().code("member:invite").build()));
        Permission readPermission = permissionRepository.findById("workspace:read")
                .orElseGet(() -> permissionRepository.save(Permission.builder().code("workspace:read").build()));
        Permission removePermission = permissionRepository.findById("member:remove")
                .orElseGet(() -> permissionRepository.save(Permission.builder().code("member:remove").build()));
        Permission taskRead = permissionRepository.findById("task:read")
                .orElseGet(() -> permissionRepository.save(Permission.builder().code("task:read").build()));
        Permission taskCreate = permissionRepository.findById("task:create")
                .orElseGet(() -> permissionRepository.save(Permission.builder().code("task:create").build()));

        // 2. Ensure global roles exist with correct permission sets (self-healing test DB)
        java.util.Set<Permission> ownerPerms = new java.util.HashSet<>(List.of(invitePermission, readPermission, removePermission, taskRead, taskCreate));
        if (!roleRepository.existsById(UUID.fromString("d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d"))) {
            roleRepository.save(Role.builder()
                    .id(UUID.fromString("d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d"))
                    .name("OWNER")
                    .description("Workspace Owner")
                    .permissions(ownerPerms)
                    .build());
        } else {
            Role owner = roleRepository.findById(UUID.fromString("d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d")).get();
            owner.setPermissions(ownerPerms);
            roleRepository.save(owner);
        }

        java.util.Set<Permission> memberPerms = new java.util.HashSet<>(List.of(invitePermission, readPermission, taskRead, taskCreate));
        if (!roleRepository.existsById(UUID.fromString("e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e"))) {
            roleRepository.save(Role.builder()
                    .id(UUID.fromString("e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e"))
                    .name("MEMBER")
                    .description("Collaborative member")
                    .permissions(memberPerms)
                    .build());
        } else {
            Role member = roleRepository.findById(UUID.fromString("e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e")).get();
            member.setPermissions(memberPerms);
            roleRepository.save(member);
        }

        java.util.Set<Permission> viewerPerms = new java.util.HashSet<>(List.of(readPermission, taskRead));
        if (!roleRepository.existsById(UUID.fromString("f3c9c4a0-f8e7-4f5c-d0a9-3a7a0c4e5f6f"))) {
            roleRepository.save(Role.builder()
                    .id(UUID.fromString("f3c9c4a0-f8e7-4f5c-d0a9-3a7a0c4e5f6f"))
                    .name("VIEWER")
                    .description("Read-only viewer")
                    .permissions(viewerPerms)
                    .build());
        } else {
            Role viewer = roleRepository.findById(UUID.fromString("f3c9c4a0-f8e7-4f5c-d0a9-3a7a0c4e5f6f")).get();
            viewer.setPermissions(viewerPerms);
            roleRepository.save(viewer);
        }

        // 3. Seed global Tenant
        company = Company.builder()
                .id(UUID.randomUUID())
                .name("Acme Corp")
                .slug("acme")
                .build();
        companyRepository.save(company);

        // 2. Seed Users
        userA = User.builder()
                .id(UUID.randomUUID())
                .email("usera@acme.com")
                .fullName("User A")
                .enabled(true)
                .build();

        userB = User.builder()
                .id(UUID.randomUUID())
                .email("userb@acme.com")
                .fullName("User B")
                .enabled(true)
                .build();

        userRepository.saveAll(List.of(userA, userB));

        tokenA = jwtService.generateAccessToken(userA);
        tokenB = jwtService.generateAccessToken(userB);
    }

    @Test
    void shouldCreateWorkspaceAndAssignCreatorAsOwner() throws Exception {
        WorkspaceController.CreateWorkspaceRequest request = new WorkspaceController.CreateWorkspaceRequest(
                company.getId(),
                "Workspace Alpha",
                "ws-alpha"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Workspace Alpha"))
                .andExpect(jsonPath("$.slug").value("ws-alpha"))
                .andReturn();

        Workspace workspace = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                Workspace.class
        );

        // Verify membership in DB
        List<WorkspaceMembership> memberships = workspaceMembershipRepository.findAllByWorkspaceId(workspace.getId());
        assertThat(memberships).hasSize(1);
        assertThat(memberships.get(0).getUserId()).isEqualTo(userA.getId());
        assertThat(memberships.get(0).getRoleId()).isEqualTo(UUID.fromString("d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d")); // OWNER
    }

    @Test
    void shouldInviteUserAndAcceptSuccessfully() throws Exception {
        // 1. Create Workspace
        TenantContext.setTenantId(company.getId());
        Workspace workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .tenantId(company.getId())
                .name("Workspace Beta")
                .slug("ws-beta")
                .build();
        workspaceService.createWorkspace(workspace, userA.getId());
        TenantContext.clear();

        // 2. Invite User B to the Workspace (with role MEMBER)
        InvitationController.InviteUserRequest inviteRequest = new InvitationController.InviteUserRequest(
                userB.getEmail(),
                UUID.fromString("e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e") // MEMBER
        );

        MvcResult inviteResult = mockMvc.perform(post("/api/v1/workspaces/" + workspace.getId() + "/invitations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        InvitationController.InviteUserResponse inviteResponse = objectMapper.readValue(
                inviteResult.getResponse().getContentAsString(),
                InvitationController.InviteUserResponse.class
                );

        String rawToken = inviteResponse.token();

        // Verify invitation status in DB
        List<Invitation> invitations = invitationRepository.findAllByWorkspaceId(workspace.getId());
        assertThat(invitations).hasSize(1);
        assertThat(invitations.get(0).getEmail()).isEqualTo(userB.getEmail());
        assertThat(invitations.get(0).getStatus()).isEqualTo("PENDING");

        // 3. User B accepts the invitation
        InvitationController.AcceptInvitationRequest acceptRequest = new InvitationController.AcceptInvitationRequest(
                rawToken,
                company.getId()
        );

        mockMvc.perform(post("/api/v1/auth/invitations/accept")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptRequest)))
                .andExpect(status().isOk());

        // Verify invite updated to ACCEPTED
        invitations = invitationRepository.findAllByWorkspaceId(workspace.getId());
        assertThat(invitations.get(0).getStatus()).isEqualTo("ACCEPTED");

        // Verify User B now has membership with role MEMBER
        List<WorkspaceMembership> memberships = workspaceMembershipRepository.findAllByWorkspaceId(workspace.getId());
        assertThat(memberships).hasSize(2);

        WorkspaceMembership membershipB = memberships.stream()
                .filter(m -> m.getUserId().equals(userB.getId()))
                .findFirst()
                .orElse(null);

        assertThat(membershipB).isNotNull();
        assertThat(membershipB.getRoleId()).isEqualTo(UUID.fromString("e2b8b3f9-e7d6-4e4b-c9f8-2f6f9b3d4c5e"));
    }

    @Test
    void shouldListAndRevokeWorkspaceMemberships() throws Exception {
        // 1. Create workspace
        TenantContext.setTenantId(company.getId());
        Workspace workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .tenantId(company.getId())
                .name("Workspace Gamma")
                .slug("ws-gamma")
                .build();
        workspaceService.createWorkspace(workspace, userA.getId());

        // Add User B as member directly for test setup
        WorkspaceMembership memberB = WorkspaceMembership.builder()
                .tenantId(company.getId())
                .workspaceId(workspace.getId())
                .userId(userB.getId())
                .roleId(UUID.fromString("f3c9c4a0-f8e7-4f5c-d0a9-3a7a0c4e5f6f")) // VIEWER
                .build();
        workspaceMembershipRepository.save(memberB);
        TenantContext.clear();

        // 2. User A retrieves members
        mockMvc.perform(get("/api/v1/workspaces/" + workspace.getId() + "/members")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));

        // 3. User A revokes User B's membership
        mockMvc.perform(delete("/api/v1/workspaces/" + workspace.getId() + "/members/" + memberB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        // Verify membership deleted in DB
        List<WorkspaceMembership> memberships = workspaceMembershipRepository.findAllByWorkspaceId(workspace.getId());
        assertThat(memberships).hasSize(1);
        assertThat(memberships.get(0).getUserId()).isEqualTo(userA.getId());
    }
}
