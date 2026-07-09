package com.crowdmonitor.service;

import com.crowdmonitor.dto.response.UserResponse;
import com.crowdmonitor.entity.Role;
import com.crowdmonitor.entity.User;
import com.crowdmonitor.exception.ResourceNotFoundException;
import com.crowdmonitor.repository.RoleRepository;
import com.crowdmonitor.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        return mapToResponse(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id)));
    }

    public UserResponse getUserByUsername(String username) {
        return mapToResponse(userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username)));
    }

    @Transactional
    public UserResponse updateUser(Long id, String fullName, String email, String roleName) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        if (fullName != null) user.setFullName(fullName);
        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("Email already in use");
            }
            user.setEmail(email);
        }
        if (roleName != null) {
            Role role = roleRepository.findByName(roleName.toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));
            user.setRole(role);
        }

        User saved = userRepository.save(user);
        auditLogService.log("USER_UPDATED", "USER", saved.getId(),
                "User '" + saved.getUsername() + "' updated");
        return mapToResponse(saved);
    }

    @Transactional
    public void toggleUserActive(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        user.setActive(!user.getActive());   // fixed: was setIsActive / getIsActive
        userRepository.save(user);
        auditLogService.log("USER_UPDATED", "USER", id,
                "User '" + user.getUsername() + "' active status set to " + user.getActive());
    }

    @Transactional
    public void changePassword(Long id, String currentPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        String username = user.getUsername();
        userRepository.deleteById(id);
        auditLogService.log("USER_DELETED", "USER", id, "User '" + username + "' deleted");
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().getName())
                .isActive(user.getActive())          // fixed: was getIsActive()
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
