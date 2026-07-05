package com.crowdmonitor.config;

import com.crowdmonitor.entity.Camera;
import com.crowdmonitor.entity.Role;
import com.crowdmonitor.entity.User;
import com.crowdmonitor.repository.CameraRepository;
import com.crowdmonitor.repository.RoleRepository;
import com.crowdmonitor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CameraRepository cameraRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedUsers();
        seedCameras();
        log.info("DataInitializer complete — system is ready.");
    }

    // ------------------------------------------------------------------
    // Roles
    // ------------------------------------------------------------------
    private void seedRoles() {
        upsertRole("ADMIN",    "Full system access");
        upsertRole("OPERATOR", "Camera management and monitoring");
        upsertRole("VIEWER",   "View-only access");
    }

    private void upsertRole(String name, String description) {
        if (roleRepository.findByName(name).isEmpty()) {
            roleRepository.save(new Role(name, description));
            log.info("Created role: {}", name);
        }
    }

    // ------------------------------------------------------------------
    // Users
    // ------------------------------------------------------------------
    private void seedUsers() {
        createUserIfAbsent("admin",     "admin@crowdmonitor.com",    "Admin@1234",    "System Administrator", "ADMIN");
        createUserIfAbsent("operator1", "operator@crowdmonitor.com", "Operator@1234", "John Operator",        "OPERATOR");
        createUserIfAbsent("viewer1",   "viewer@crowdmonitor.com",   "Viewer@1234",   "Jane Viewer",          "VIEWER");
    }

    private void createUserIfAbsent(String username, String email, String rawPassword,
                                     String fullName, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));

        var existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            // SELF-HEAL: a stale row created by an earlier/broken build of this
            // project (bad password hash, wrong role, or disabled flag) would
            // otherwise persist forever, since this method previously skipped
            // any user that already existed. That is exactly why "viewer1"
            // kept failing to log in even after the auth code itself was
            // fixed — the corrupted row from before was never touched again.
            // Re-assert the known-good demo credentials/role/status here so
            // the seeded demo accounts always work after a restart.
            User user = existing.get();
            boolean changed = false;

            if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
                user.setPassword(passwordEncoder.encode(rawPassword));
                changed = true;
            }
            if (user.getRole() == null || !roleName.equals(user.getRole().getName())) {
                user.setRole(role);
                changed = true;
            }
            if (!Boolean.TRUE.equals(user.getActive())) {
                user.setActive(true);
                changed = true;
            }
            if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                user.setEmailVerified(true);
                changed = true;
            }

            if (changed) {
                userRepository.save(user);
                log.info("Repaired seeded demo user: {} (role={})", username, roleName);
            } else {
                log.debug("User '{}' already exists and is healthy — skipping.", username);
            }
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(true);           // fixed: was setIsActive()
        user.setEmailVerified(true);    // fixed: was setIsEmailVerified()

        userRepository.save(user);
        log.info("Seeded user: {} (role={})", username, roleName);
    }

    // ------------------------------------------------------------------
    // Sample cameras
    // ------------------------------------------------------------------
    private void seedCameras() {
        if (cameraRepository.count() > 0) {
            log.debug("Cameras already exist — skipping seed.");
            return;
        }
        User admin = userRepository.findByUsername("admin").orElse(null);

        List<CameraSpec> specs = List.of(
            new CameraSpec("Auditorium Cam 1",  "CCTV",   "College Auditorium", 500, "rtsp://192.168.1.100:554/stream1", "Main entrance camera for auditorium"),
            new CameraSpec("Seminar Hall Cam",  "CCTV",   "Seminar Hall",       150, "rtsp://192.168.1.101:554/stream1", "Seminar hall overhead camera"),
            new CameraSpec("Temple Entrance",   "MOBILE", "Temple Entrance",    300, "http://192.168.1.50:8080/video",   "Mobile camera at temple gate"),
            new CameraSpec("Bus Stand Monitor", "CCTV",   "Bus Stand",          700, "rtsp://192.168.1.102:554/stream1", "Bus stand crowd monitoring"),
            new CameraSpec("Mall Food Court",   "CCTV",   "Mall Food Court",    250, "rtsp://192.168.1.103:554/stream1", "Food court density monitoring")
        );

        for (CameraSpec s : specs) {
            Camera cam = new Camera();
            cam.setCameraName(s.name());
            cam.setCameraType(Camera.CameraType.valueOf(s.type()));
            cam.setLocationName(s.location());
            cam.setMaximumCapacity(s.capacity());
            cam.setStreamUrl(s.url());
            cam.setDescription(s.description());
            cam.setStatus(Camera.CameraStatus.INACTIVE);
            cam.setCreatedBy(admin);
            cameraRepository.save(cam);
        }
        log.info("Seeded {} sample cameras.", specs.size());
    }

    private record CameraSpec(String name, String type, String location,
                               int capacity, String url, String description) {}
}
