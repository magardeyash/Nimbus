package com.nimbus.team;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    @Transactional
    public Team createTeam(Team team) {
        return teamRepository.save(team);
    }

    @Transactional
    public TeamMembership addMember(UUID teamId, UUID userId, UUID tenantId) {
        TeamMembership membership = TeamMembership.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .build();
        return teamMembershipRepository.save(membership);
    }

    @Transactional(readOnly = true)
    public List<Team> getTeamsByWorkspace(UUID workspaceId) {
        return teamRepository.findAllByWorkspaceId(workspaceId);
    }
}
