package com.neelastack.service;

import com.neelastack.dto.engagement.AdminStaffDto;
import com.neelastack.dto.engagement.AdminStaffInviteRequest;
import com.neelastack.dto.engagement.AdminStaffUpdateRequest;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.repository.UserRepository;
import com.neelastack.security.CurrentUserProvider;
import com.neelastack.security.OneTimeTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminStaffService {
    private static final String NAMESPACE = "admin_invitation";
    private static final Duration TTL = Duration.ofDays(2);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final OneTimeTokenService oneTimeTokenService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.site.frontend-url}")
    private String frontendUrl;

    private User requireSuperAdmin() {
        User current = currentUserProvider.get();
        if (current.getRole() != Role.SUPERADMIN) throw new AccessDeniedException("SUPERADMIN required");
        return current;
    }

    @Transactional(readOnly = true)
    public List<AdminStaffDto> list() {
        requireSuperAdmin();
        return userRepository.findByRoleInOrderByFullNameAsc(List.of(Role.ADMIN, Role.SUPERADMIN)).stream()
                .map(this::toDto).toList();
    }

    @Transactional
    public AdminStaffDto invite(AdminStaffInviteRequest request) {
        requireSuperAdmin();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) throw new BadRequestException("An account already exists for this email");
        String unusablePassword = randomSecret();
        User user = User.builder().fullName(request.fullName().trim()).email(email)
                .password(passwordEncoder.encode(unusablePassword)).role(Role.ADMIN).enabled(false)
                .emailVerified(false).invitationPending(true).mfaEnabled(false).mustChangePassword(true).build();
        userRepository.save(user);
        String token = oneTimeTokenService.issue(NAMESPACE, user.getId().toString(), TTL);
        emailService.sendAdminInvitationEmail(user.getEmail(), user.getFullName(), frontendUrl + "/accept-admin-invitation?token=" + token);
        return toDto(user);
    }

    @Transactional
    public AdminStaffDto update(UUID id, AdminStaffUpdateRequest request) {
        User current = requireSuperAdmin();
        User user = userRepository.findById(id).orElseThrow(() -> new BadRequestException("Staff account not found"));
        if (user.getId().equals(current.getId()) && (!request.enabled() || request.role() != Role.SUPERADMIN)) {
            throw new BadRequestException("You cannot demote or disable the active SUPERADMIN account");
        }
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        return toDto(user);
    }

    private AdminStaffDto toDto(User u) {
        return AdminStaffDto.builder().id(u.getId()).fullName(u.getFullName()).email(u.getEmail()).role(u.getRole())
                .enabled(u.isEnabled()).mfaEnabled(u.isMfaEnabled()).mustChangePassword(u.isMustChangePassword()).createdAt(u.getCreatedAt()).build();
    }
    private String randomSecret() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
