package com.nimbus.workspace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    @Autowired
    public WorkspaceService(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional
    public Workspace createWorkspace(Workspace workspace) {
        return workspaceRepository.save(workspace);
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
