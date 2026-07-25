package com.nimbus.rbac;

import com.nimbus.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller strictly for integration testing purposes.
 * It exposes endpoints mapped under /api/v1/workspaces to trigger TenantResolutionInterceptor
 * and validates CustomPermissionEvaluator annotations.
 */
@RestController
@RequestMapping("/api/v1/workspaces")
public class TestWorkspaceController {

    @GetMapping("/{workspaceId}/test-read")
    @PreAuthorize("hasPermission(#workspaceId, 'workspace', 'task:read')")
    public ResponseEntity<String> testRead(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok("Read Success. Tenant: " + TenantContext.getTenantId());
    }

    @PostMapping("/{workspaceId}/test-write")
    @PreAuthorize("hasPermission(#workspaceId, 'workspace', 'task:create')")
    public ResponseEntity<String> testWrite(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok("Write Success. Tenant: " + TenantContext.getTenantId());
    }
}
