package com.nimbus.team;

import com.nimbus.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    public record CreateTeamRequest(
            @NotBlank String name,
            @NotBlank String slug
    ) {}

    public record AddMemberRequest(
            @NotNull UUID userId
    ) {}

    @PostMapping
    @PreAuthorize("hasPermission(#workspaceId, 'workspace', 'team:create')")
    public ResponseEntity<Team> createTeam(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateTeamRequest request) {

        UUID tenantId = TenantContext.getTenantId();

        Team team = Team.builder()
                .tenantId(tenantId)
                .workspaceId(workspaceId)
                .name(request.name())
                .slug(request.slug())
                .build();

        Team savedTeam = teamService.createTeam(team);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedTeam);
    }

    @GetMapping
    @PreAuthorize("hasPermission(#workspaceId, 'workspace', 'team:read')")
    public ResponseEntity<List<Team>> getTeams(@PathVariable UUID workspaceId) {
        List<Team> teams = teamService.getTeamsByWorkspace(workspaceId);
        return ResponseEntity.ok(teams);
    }

    @PostMapping("/{teamId}/members")
    @PreAuthorize("hasPermission(#workspaceId, 'workspace', 'team:update')")
    public ResponseEntity<TeamMembership> addMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID teamId,
            @Valid @RequestBody AddMemberRequest request) {

        UUID tenantId = TenantContext.getTenantId();
        TeamMembership membership = teamService.addMember(teamId, request.userId(), tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(membership);
    }
}
