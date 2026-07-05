package com.crowdmonitor.service;

import com.crowdmonitor.dto.request.*;
import com.crowdmonitor.dto.response.AuthResponse;
import com.crowdmonitor.dto.response.UserResponse;
import com.crowdmonitor.entity.RefreshToken;
import com.crowdmonitor.entity.Role;
import com.crowdmonitor.entity.User;
import com.crowdmonitor.exception.ResourceNotFoundException;
import com.crowdmonitor.repository.RefreshTokenRepository;
import com.crowdmonitor.repository.RoleRepository;
import com.crowdmonitor.repository.UserRepository;
import com.crowdmonitor.security.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    @Value("${app.jwt.expiration}")
    private Long jwtExpiration;

    @Value("${app.jwt.refresh-expiration}")
    private Long refreshExpiration;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        Role role = roleRepository.findByName(request.getRole().toUpperCase())
                .orElseGet(() -> roleRepository.findByName("VIEWER")
                        .orElseThrow(() -> new ResourceNotFoundException("Role not found")));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(role);
        user.setActive(true);           // fixed: was setIsActive()
        user.setEmailVerified(true);    // fixed: was setIsEmailVerified()

        userRepository.save(user);

        auditLogService.logForUser(user, "USER_ADDED", "USER", user.getId(),
                "Account registered with role " + role.getName());

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role.getName());
        claims.put("userId", user.getId());

        String accessToken = jwtUtil.generateToken(userDetails, claims);
        String refreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(mapToUserResponse(user))
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // authenticationManager.authenticate() internally invokes
        // UserDetailsServiceImpl.loadUserByUsername(), which performs DB query #1
        // and also runs passwordEncoder.matches() against the stored hash.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsernameOrEmail(), request.getPassword())
        );

        // PERFORMANCE FIX: the original code then ran a SECOND identical query
        // here (findByUsernameOrEmail) and a THIRD query later via
        // userDetailsService.loadUserByUsername() just to rebuild UserDetails
        // for JWT generation — fetching the exact same row 3 times for one
        // login. We now fetch the User entity exactly once and build the
        // UserDetails needed for JWT claims directly from it in memory.
        User user = userRepository.findByUsernameOrEmail(
                        request.getUsernameOrEmail(), request.getUsernameOrEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        auditLogService.logForUser(user, "LOGIN", "USER", user.getId(),
                "User logged in successfully");

        UserDetails userDetails = buildUserDetails(user);
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().getName());
        claims.put("userId", user.getId());

        String accessToken = jwtUtil.generateToken(userDetails, claims);
        refreshTokenRepository.deleteByUser(user);
        String refreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(mapToUserResponse(user))
                .build();
    }

    /**
     * Builds a Spring Security UserDetails directly from an already-loaded
     * User entity, avoiding a redundant DB round-trip through
     * UserDetailsServiceImpl when we already have the full entity in hand.
     * Mirrors the exact same field mapping UserDetailsServiceImpl.loadUserByUsername
     * uses, so JWT generation behaves identically either way.
     */
    private UserDetails buildUserDetails(User user) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(java.util.List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_" + user.getRole().getName())))
                .accountExpired(false)
                .accountLocked(!user.getActive())
                .credentialsExpired(false)
                .disabled(!user.getActive())
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new IllegalArgumentException("Refresh token expired");
        }

        User user = refreshToken.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().getName());
        claims.put("userId", user.getId());

        String newAccessToken = jwtUtil.generateToken(userDetails, claims);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(mapToUserResponse(user))
                .build();
    }

    @Transactional
    public void logout(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            User user = rt.getUser();
            refreshTokenRepository.delete(rt);
            auditLogService.logForUser(user, "LOGOUT", "USER", user.getId(),
                    "User logged out");
        });
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No user found with email: " + request.getEmail()));

        String resetToken = UUID.randomUUID().toString();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetToken);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (user.getResetTokenExpiry() == null
                || LocalDateTime.now().isAfter(user.getResetTokenExpiry())) {
            throw new IllegalArgumentException("Reset token has expired");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    private String createRefreshToken(User user) {
        RefreshToken rt = new RefreshToken();
        rt.setToken(UUID.randomUUID().toString());
        rt.setUser(user);
        rt.setExpiryDate(LocalDateTime.now().plusSeconds(refreshExpiration / 1000));
        refreshTokenRepository.save(rt);
        return rt.getToken();
    }

    public UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().getName())
                .isActive(user.getActive())           // fixed: was getIsActive()
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
