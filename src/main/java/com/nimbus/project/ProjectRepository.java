package com.nimbus.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByWorkspaceIdAndSlug(UUID workspaceId, String slug);
    List<Project> findAllByWorkspaceId(UUID workspaceId);
    List<Project> findAllByTeamId(UUID teamId);
}
