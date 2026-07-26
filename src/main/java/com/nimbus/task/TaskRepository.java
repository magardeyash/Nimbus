package com.nimbus.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findAllByWorkspaceId(UUID workspaceId);
    List<Task> findAllByProjectId(UUID projectId);
    List<Task> findAllByAssigneeId(UUID assigneeId);
}
