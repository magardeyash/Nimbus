package com.nimbus.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request/Response DTOs for the auth API.
 */
public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            String fullName
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresIn
    ) {
        public static AuthResponse of(String token, long expiresInMs) {
            return new AuthResponse(token, "Bearer", expiresInMs / 1000);
        }
    }
}
