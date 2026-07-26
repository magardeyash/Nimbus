package com.nimbus.task;

import com.nimbus.identity.UserPrincipal;
import com.nimbus.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    public record CreateTaskRequest(
            UUID projectId, // optional
            @NotBlank String title,
            String description,
            String status, // BACKLOG, TODO, etc.
            String priority, // NO_PRIORITY, LOW, etc.
            UUID assigneeId
    ) {}

    @PostMapping
    @PreAuthorize("hasPermission(#workspaceId, 'workspace', 'task:create')")
    public ResponseEntity<Task> createTask(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateTaskRequest request) {

        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID tenantId = TenantContext.getTenantId();

        Task task = Task.builder()
                .tenantId(tenantId)
                .workspaceId(workspaceId)
                .projectId(request.projectId())
                .title(request.title())
                .description(request.description())
                .status(request.status() != null ? request.status() : "BACKLOG")
                .priority(request.priority() != null ? request.priority() : "MEDIUM")
                .creatorId(principal.getUserId())
                .assigneeId(request.assigneeId())
                .build();

        Task savedTask = taskService.createTask(task);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedTask);
    }

    @GetMapping
    @PreAuthorize("hasPermission(#workspaceId, 'workspace', 'task:read')")
    public ResponseEntity<List<Task>> getTasks(@PathVariable UUID workspaceId) {
        List<Task> tasks = taskService.getTasksByWorkspace(workspaceId);
        return ResponseEntity.ok(tasks);
    }
}
