package com.nimbus.tenant;

import com.nimbus.identity.UserPrincipal;
import com.nimbus.workspace.Workspace;
import com.nimbus.workspace.WorkspaceMembership;
import com.nimbus.workspace.WorkspaceMembershipRepository;
import com.nimbus.workspace.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

/**
 * Spring MVC Interceptor that intercepts workspace-scoped endpoints.
 * Resolves the tenant_id associated with the target workspace, verifies the authenticated user's membership,
 * and populates the TenantContext for the duration of the request.
 */
@Component
public class TenantResolutionInterceptor implements HandlerInterceptor {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;

    public TenantResolutionInterceptor(WorkspaceRepository workspaceRepository,
                                       WorkspaceMembershipRepository workspaceMembershipRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        if (pathVariables != null && pathVariables.containsKey("workspaceId")) {
            String workspaceIdStr = pathVariables.get("workspaceId");
            UUID workspaceId;
            try {
                workspaceId = UUID.fromString(workspaceIdStr);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid workspace ID format");
            }

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
            }

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            UUID userId = principal.getUserId();

            // Fetch the workspace to resolve the tenant_id
            Workspace workspace = workspaceRepository.findById(workspaceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

            // Verify user membership in this workspace
            workspaceMembershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this workspace"));

            // Propagate the tenant context down to the execution thread
            TenantContext.setTenantId(workspace.getTenantId());
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // ALWAYS clear the context to prevent thread local leaks into reused pool threads
        TenantContext.clear();
    }
}
