package com.nimbus.rbac;

import com.nimbus.identity.UserPrincipal;
import com.nimbus.workspace.WorkspaceMembership;
import com.nimbus.workspace.WorkspaceMembershipRepository;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.UUID;

/**
 * Custom Spring Security PermissionEvaluator that checks if the authenticated user
 * has a specific granular permission for a workspace.
 */
@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final RoleRepository roleRepository;

    public CustomPermissionEvaluator(WorkspaceMembershipRepository workspaceMembershipRepository,
                                    RoleRepository roleRepository) {
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        // Not used in our @PreAuthorize annotations, which pass (id, type, permission)
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || targetId == null || targetType == null || !(permission instanceof String)) {
            return false;
        }

        if (!(authentication.getPrincipal() instanceof UserPrincipal)) {
            return false;
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID userId = principal.getUserId();

        UUID workspaceId;
        try {
            workspaceId = UUID.fromString(targetId.toString());
        } catch (IllegalArgumentException e) {
            return false;
        }

        String requiredPermission = (String) permission;

        // Retrieve membership -> retrieve role -> check permissions set matches the required code
        return workspaceMembershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .flatMap(membership -> roleRepository.findById(membership.getRoleId()))
                .map(role -> role.getPermissions().stream()
                        .anyMatch(p -> p.getCode().equals(requiredPermission)))
                .orElse(false);
    }
}

