package com.neelastack.config;

import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Replaces the old seeded default admin (admin@neelastack.com / ChangeMe@123 -- a known,
 * published credential that was never safe once it existed in version control; purged in
 * migration V24). Provisions exactly one admin account, on first boot only, from environment
 * variables -- never from a hardcoded value -- and forces both a password change and MFA
 * enrollment before that account can do anything else.
 *
 * Runs on every profile (not just prod) so a fresh dev/staging database also gets a real,
 * operator-chosen admin instead of silently having none -- but see the "does nothing if an
 * admin already exists" guard below, which makes this a true one-time bootstrap, not a
 * recurring reset.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin-bootstrap.email:}")
    private String bootstrapEmail;

    @Value("${app.admin-bootstrap.password:}")
    private String bootstrapPassword;

    @Value("${app.admin-bootstrap.full-name:Neelastack Admin}")
    private String bootstrapFullName;

    @PostConstruct
    @Transactional
    public void bootstrapAdminIfNeeded() {
        boolean anyAdminExists = userRepository.existsByRole(Role.ADMIN);
        if (anyAdminExists) {
            return;
        }

        if (isBlank(bootstrapEmail) || isBlank(bootstrapPassword)) {
            // No admin exists yet and no bootstrap credentials were supplied. This is a real,
            // fail-loud problem (there is now no way to log in as an admin at all) rather than
            // something to silently paper over with a hardcoded fallback -- that hardcoded
            // fallback is exactly the vulnerability this replaces. Logged, not thrown: an
            // app with zero admins can still legitimately serve public/client traffic (e.g. a
            // fresh CI database that intentionally never sets these), so refusing to start
            // the whole application over it would be its own kind of availability bug.
            log.warn("No admin account exists and ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD " +
                    "are not set -- skipping admin bootstrap. Set both environment variables and " +
                    "restart to provision the first admin account.");
            return;
        }

        if (bootstrapPassword.length() < 12) {
            log.error("ADMIN_BOOTSTRAP_PASSWORD is shorter than 12 characters -- refusing to " +
                    "bootstrap an admin account with a weak password. Set a stronger value and restart.");
            return;
        }

        User admin = User.builder()
                .fullName(bootstrapFullName)
                .email(bootstrapEmail.trim().toLowerCase(Locale.ROOT))
                .password(passwordEncoder.encode(bootstrapPassword))
                .role(Role.ADMIN)
                .enabled(true)
                .emailVerified(true)
                // Forced true: the operator-supplied bootstrap password is a shared secret by
                // construction (env var, deploy script, whoever provisioned the box) -- it must
                // be changed to something only the real admin knows before anything else happens.
                .mustChangePassword(true)
                .build();

        userRepository.save(admin);
        log.info("Bootstrapped initial admin account ({}). This account must change its password " +
                "and enroll MFA before it can access any other admin functionality.", admin.getEmail());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
