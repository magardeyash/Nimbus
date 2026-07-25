package com.nimbus.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        AuthDtos.RegisterRequest request = new AuthDtos.RegisterRequest(
                "alice@nimbus.com",
                "password123",
                "Alice Jenkins"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber());

        // Verify DB State
        var userOpt = userRepository.findByEmail("alice@nimbus.com");
        assertThat(userOpt).isPresent();
        assertThat(userOpt.get().getFullName()).isEqualTo("Alice Jenkins");
        assertThat(passwordEncoder.matches("password123", userOpt.get().getPasswordHash())).isTrue();
    }

    @Test
    void shouldFailRegistrationIfEmailAlreadyExists() throws Exception {
        // Register user initially
        User existingUser = User.builder()
                .email("duplicate@nimbus.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Existing User")
                .enabled(true)
                .build();
        userRepository.save(existingUser);

        AuthDtos.RegisterRequest request = new AuthDtos.RegisterRequest(
                "duplicate@nimbus.com",
                "newpassword123",
                "Duplicate Registration"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict()); // BadCredentialsException translated to conflict or similar (depending on handler; BadCredentialsException returns 401/409/400. In Controller we don't catch, standard exception handlers map BadCredentialsException. Let's make sure exception mapping is handled)
    }

    @Test
    void shouldLoginSuccessfullyWithValidCredentials() throws Exception {
        User user = User.builder()
                .email("bob@nimbus.com")
                .passwordHash(passwordEncoder.encode("bobsecure456"))
                .fullName("Bob Miller")
                .enabled(true)
                .build();
        userRepository.save(user);

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("bob@nimbus.com", "bobsecure456");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void shouldFailLoginWithInvalidCredentials() throws Exception {
        User user = User.builder()
                .email("charlie@nimbus.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Charlie")
                .enabled(true)
                .build();
        userRepository.save(user);

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("charlie@nimbus.com", "wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRotateRefreshTokensOnRefreshRequest() throws Exception {
        // 1. Register a user
        AuthDtos.RegisterRequest registerRequest = new AuthDtos.RegisterRequest(
                "david@nimbus.com",
                "password123",
                "David"
        );

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andReturn();

        AuthDtos.AuthResponse regResponse = objectMapper.readValue(
                regResult.getResponse().getContentAsString(),
                AuthDtos.AuthResponse.class
        );

        String firstRefreshToken = regResponse.refreshToken();

        // 2. Perform token refresh
        AuthDtos.RefreshRequest refreshRequest = new AuthDtos.RefreshRequest(firstRefreshToken);

        MvcResult refResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        AuthDtos.AuthResponse refResponse = objectMapper.readValue(
                refResult.getResponse().getContentAsString(),
                AuthDtos.AuthResponse.class
        );

        // Verify that the old refresh token is marked revoked in the database
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(2);

        RefreshToken oldToken = tokens.stream()
                .filter(t -> t.isRevoked())
                .findFirst()
                .orElse(null);

        RefreshToken newToken = tokens.stream()
                .filter(t -> !t.isRevoked())
                .findFirst()
                .orElse(null);

        assertThat(oldToken).isNotNull();
        assertThat(newToken).isNotNull();
        assertThat(oldToken.getReplacedByToken()).isEqualTo(newToken.getTokenHash());
    }

    @Test
    void shouldRevokeAllSessionsOnRefreshTokenReuse() throws Exception {
        // 1. Register a user
        AuthDtos.RegisterRequest registerRequest = new AuthDtos.RegisterRequest(
                "breach@nimbus.com",
                "password123",
                "User"
        );

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andReturn();

        AuthDtos.AuthResponse regResponse = objectMapper.readValue(
                regResult.getResponse().getContentAsString(),
                AuthDtos.AuthResponse.class
        );

        String rawToken = regResponse.refreshToken();

        // 2. Refresh once (works fine, revokes rawToken, issues rawToken2)
        AuthDtos.RefreshRequest firstRefresh = new AuthDtos.RefreshRequest(rawToken);
        MvcResult refResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRefresh)))
                .andExpect(status().isOk())
                .andReturn();

        AuthDtos.AuthResponse refResponse = objectMapper.readValue(
                refResult.getResponse().getContentAsString(),
                AuthDtos.AuthResponse.class
        );

        String rawToken2 = refResponse.refreshToken();

        // 3. Attempt to reuse rawToken (the old one)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRefresh)))
                .andExpect(status().isUnauthorized());

        // 4. Assert that ALL refresh tokens for this user are now revoked in the DB
        List<RefreshToken> allTokens = refreshTokenRepository.findAll();
        assertThat(allTokens).isNotEmpty();
        assertThat(allTokens).allMatch(RefreshToken::isRevoked);
    }

    @Test
    void shouldLogoutSuccessfully() throws Exception {
        // Register a user
        AuthDtos.RegisterRequest registerRequest = new AuthDtos.RegisterRequest(
                "logout@nimbus.com",
                "password123",
                "User"
        );

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andReturn();

        AuthDtos.AuthResponse regResponse = objectMapper.readValue(
                regResult.getResponse().getContentAsString(),
                AuthDtos.AuthResponse.class
        );

        String rawToken = regResponse.refreshToken();

        AuthDtos.RefreshRequest logoutRequest = new AuthDtos.RefreshRequest(rawToken);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isNoContent());

        // Assert token is revoked in DB
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).isRevoked()).isTrue();
    }
}
