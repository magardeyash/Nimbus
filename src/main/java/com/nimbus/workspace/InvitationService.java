package com.nimbus.workspace;

import com.nimbus.rbac.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public String inviteUser(UUID workspaceId, String email, UUID roleId, UUID tenantId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));

        roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        Invitation invitation = Invitation.builder()
                .tenantId(tenantId)
                .workspaceId(workspaceId)
                .email(email)
                .roleId(roleId)
                .tokenHash(tokenHash)
                .status("PENDING")
                .expiresAt(LocalDateTime.now().plusHours(48))
                .build();

        invitationRepository.save(invitation);

        return rawToken;
    }

    @Transactional
    public void acceptInvitation(String rawToken, UUID inviteeUserId) {
        String tokenHash = hashToken(rawToken);

        Invitation invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid invitation token"));

        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus("EXPIRED");
            invitationRepository.save(invitation);
            throw new BadCredentialsException("Invitation token has expired");
        }

        if (!"PENDING".equals(invitation.getStatus())) {
            throw new BadCredentialsException("Invitation is no longer pending");
        }

        invitation.setStatus("ACCEPTED");
        invitationRepository.save(invitation);

        WorkspaceMembership membership = WorkspaceMembership.builder()
                .tenantId(invitation.getTenantId())
                .workspaceId(invitation.getWorkspaceId())
                .userId(inviteeUserId)
                .roleId(invitation.getRoleId())
                .build();

        workspaceMembershipRepository.save(membership);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 hashing algorithm not available", e);
        }
    }
}
