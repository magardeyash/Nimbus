package com.nimbus.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}
    /**
     * Payload sent by the client during user registration.
     */
    public record RegisterRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email format")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, message = "Password must be at least 8 characters long")
            String password,

            @NotBlank(message = "Full name is required")
            String fullName
    ) {}

    /**
     * Payload sent by the client during login.
     */
    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email format")
            String email,

            @NotBlank(message = "Password is required")
            String password
    ) {}

    /**
     * Payload sent by the client to request a new access token via refresh token.
     */
    public record RefreshRequest(
            @NotBlank(message = "Refresh token is required")
            String refreshToken
    ) {}

    /**
     * Payload returned to the client upon successful registration, login, or token refresh.
     */
    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn
    ) {
        public static AuthResponse of(String accessToken, String refreshToken, long expiresInMs) {
            return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInMs/1000);
        }
    }
}
