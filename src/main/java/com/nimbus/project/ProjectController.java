package com.nimbus.project;

import com.nimbus.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    public record CreateProjectRequest(
            UUID teamId, // optional reference to a team
            @NotBlank String name,
            @NotBlank String slug,
            String description
    ) {}

    @PostMapping
    @PreAuthorize("hasPermission(#workspaceId, 'workspace', 'project:create')")
    public ResponseEntity<Project> createProject(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateProjectRequest request) {

        UUID tenantId = TenantContext.getTenantId();

        Project project = Project.builder()
                .tenantId(tenantId)
                .workspaceId(workspaceId)
                .teamId(request.teamId())
                .name(request.name())
                .slug(request.slug())
                .description(request.description())
                .status("ACTIVE")
                .build();

        Project savedProject = projectService.createProject(project);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedProject);
    }

    @GetMapping
    @PreAuthorize("hasPermission(#workspaceId, 'workspace', 'project:read')")
    public ResponseEntity<List<Project>> getProjects(@PathVariable UUID workspaceId) {
        List<Project> projects = projectService.getProjectsByWorkspace(workspaceId);
        return ResponseEntity.ok(projects);
    }
}
