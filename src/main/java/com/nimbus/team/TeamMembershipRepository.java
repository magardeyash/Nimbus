package com.nimbus.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamMembershipRepository extends JpaRepository<TeamMembership, UUID> {
    Optional<TeamMembership> findByTeamIdAndUserId(UUID teamId, UUID userId);
    List<TeamMembership> findAllByTeamId(UUID teamId);
    List<TeamMembership> findAllByUserId(UUID userId);
}
