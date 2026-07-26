package com.nimbus.workspace;

import com.nimbus.identity.UserPrincipal;
import com.nimbus.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;

    public WorkspaceController(WorkspaceService workspaceService,
                               WorkspaceMembershipRepository workspaceMembershipRepository) {
        this.workspaceService = workspaceService;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
    }

    public record CreateWorkspaceRequest(
            @NotNull UUID tenantId,
            @NotBlank String name,
            @NotBlank String slug
    ) {}

    @PostMapping
    public ResponseEntity<Workspace> createWorkspace(@Valid @RequestBody CreateWorkspaceRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // Propagate tenant context manually for workspace creation
        TenantContext.setTenantId(request.tenantId());

        Workspace workspace = Workspace.builder()
                .tenantId(request.tenantId())
                .name(request.name())
                .slug(request.slug())
                .build();

        try {
            Workspace savedWorkspace = workspaceService.createWorkspace(workspace, principal.getUserId());
            return ResponseEntity.status(HttpStatus.CREATED).body(savedWorkspace);
        } finally {
            TenantContext.clear();
        }
    }

    @GetMapping("/{workspaceId}/members")
    @PreAuthorize("hasPermission(#workspaceId, 'workspace', 'workspace:read')")
    public ResponseEntity<List<WorkspaceMembership>> getMembers(@PathVariable UUID workspaceId) {
        List<WorkspaceMembership> members = workspaceMembershipRepository.findAllByWorkspaceId(workspaceId);
        return ResponseEntity.ok(members);
    }

    @DeleteMapping("/{workspaceId}/members/{membershipId}")
    @PreAuthorize("hasPermission(#workspaceId, 'workspace', 'member:remove')")
    public ResponseEntity<Void> removeMember(@PathVariable UUID workspaceId, @PathVariable UUID membershipId) {
        workspaceMembershipRepository.deleteById(membershipId);
        return ResponseEntity.noContent().build();
    }
}
