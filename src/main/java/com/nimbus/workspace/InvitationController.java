package com.nimbus.workspace;

import com.nimbus.identity.UserPrincipal;
import com.nimbus.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    public record InviteUserRequest(
            @NotBlank @Email String email,
            @NotNull UUID roleId
    ) {}

    public record InviteUserResponse(
            String token
    ) {}

    public record AcceptInvitationRequest(
            @NotBlank String token,
            @NotNull UUID tenantId
    ) {}

    /**
     * Secures endpoint under workspace paths.
     * Interceptor automatically populates TenantContext for this request lifecycle.
     */
    @PostMapping("/workspaces/{workspaceId}/invitations")
    @PreAuthorize("hasPermission(#workspaceId, 'workspace', 'member:invite')")
    public ResponseEntity<InviteUserResponse> inviteUser(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody InviteUserRequest request) {

        UUID tenantId = TenantContext.getTenantId();
        String rawToken = invitationService.inviteUser(workspaceId, request.email(), request.roleId(), tenantId);

        return ResponseEntity.status(HttpStatus.CREATED).body(new InviteUserResponse(rawToken));
    }

    /**
     * Public endpoint to accept invitations.
     * Propagates tenant context manually before query verification.
     */
    @PostMapping("/auth/invitations/accept")
    public ResponseEntity<Void> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 1. Manually set TenantContext using the payload's tenant ID
        TenantContext.setTenantId(request.tenantId());

        try {
            // 2. Accept token and create workspace membership
            invitationService.acceptInvitation(request.token(), principal.getUserId());
            return ResponseEntity.ok().build();
        } finally {
            // 3. Always clear context
            TenantContext.clear();
        }
    }
}
