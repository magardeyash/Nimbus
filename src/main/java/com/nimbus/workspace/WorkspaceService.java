package com.nimbus.workspace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;

    @Autowired
    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            WorkspaceMembershipRepository workspaceMembershipRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
    }

    @Transactional
    public Workspace createWorkspace(Workspace workspace) {
        return workspaceRepository.save(workspace);
    }

    @Transactional
    public Workspace createWorkspace(Workspace workspace, UUID creatorUserId) {
        Workspace savedWorkspace = workspaceRepository.save(workspace);

        // Seeded global OWNER Role ID: d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d
        WorkspaceMembership membership = WorkspaceMembership.builder()
                .tenantId(workspace.getTenantId())
                .workspaceId(savedWorkspace.getId())
                .userId(creatorUserId)
                .roleId(UUID.fromString("d1b7a2e8-d6c5-4d3a-b8e7-1e5f8a2c3b4d"))
                .build();

        workspaceMembershipRepository.save(membership);
        return savedWorkspace;
    }

    @Transactional(readOnly = true)
    public List<Workspace> getAllWorkspaces() {
        return workspaceRepository.findAll();
    }

    @Transactional
    public void deleteAllWorkspaces() {
        workspaceRepository.deleteAll();
    }
}

