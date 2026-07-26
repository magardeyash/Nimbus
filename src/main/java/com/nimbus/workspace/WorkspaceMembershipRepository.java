package com.nimbus.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {
    Optional<WorkspaceMembership> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
    List<WorkspaceMembership> findAllByUserId(UUID userId);
    List<WorkspaceMembership> findAllByWorkspaceId(UUID workspaceId);
}
