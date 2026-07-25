package com.nimbus.identity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.UUID;

import static org.aspectj.weaver.tools.cache.SimpleCacheFactory.enabled;

public class UserPrincipal implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;

    private UserPrincipal(UUID userId, String email, String passwordHash, boolean enabled) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.isEnabled()
        );
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }
}
