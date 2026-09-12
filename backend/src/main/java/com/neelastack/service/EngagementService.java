package com.neelastack.service;

import com.neelastack.dto.engagement.EngagementDto;
import com.neelastack.dto.engagement.EngagementRequest;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.EngagementStatus;
import com.neelastack.entity.Inquiry;
import com.neelastack.entity.ProjectActivityType;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.EngagementRepository;
import com.neelastack.repository.InquiryRepository;
import com.neelastack.repository.UserRepository;
import com.neelastack.security.CurrentUserProvider;
import com.neelastack.security.OneTimeTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EngagementService {

    private final EngagementRepository engagementRepository;
    private final UserRepository userRepository;
    private final InquiryRepository inquiryRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokenService oneTimeTokenService;
    private final EmailService emailService;
    private final ProjectActivityService projectActivityService;

    @Value("${app.site.frontend-url}")
    private String frontendUrl;

    private static final String INVITE_NAMESPACE = "client_invitation";
    private static final Duration INVITE_TTL = Duration.ofDays(7);

    @Transactional
    public EngagementDto create(EngagementRequest request) {
        String normalizedClientEmail = normalizeEmail(request.clientEmail());

        Inquiry inquiry = request.inquiryId() != null
                ? inquiryRepository.findById(request.inquiryId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Inquiry not found: " + request.inquiryId()))
                : null;

        /*
         * Business-integrity guard: an engagement created from an inquiry must stay attached
         * to the same person. Both records existing is not enough — otherwise an admin could
         * accidentally pair Client B's account with Client A's inquiry and poison the CRM,
         * attribution, email, and reporting data downstream.
         */
        if (inquiry != null && !normalizeEmail(inquiry.getEmail()).equals(normalizedClientEmail)) {
            throw new BadRequestException(
                    "The selected inquiry does not belong to the supplied client email");
        }

        // Section 5 of the client-workspace review: don't require the client to already have
        // a registered account. If none exists for this email, create an invited placeholder
        // instead of failing — the client activates it via the emailed link (or Google
        // sign-in with the same address) rather than visiting /register separately first.
        boolean isNewInvite = userRepository.findByEmail(normalizedClientEmail).isEmpty();
        User client = userRepository.findByEmail(normalizedClientEmail)
                .orElseGet(() -> inviteClient(normalizedClientEmail, resolveClientName(request, inquiry)));

        Engagement engagement = Engagement.builder()
                .client(client)
                .inquiry(inquiry)
                .title(request.title())
                .description(request.description())
                .status(EngagementStatus.ONBOARDING)
                .startDate(request.startDate())
                .targetEndDate(request.targetEndDate())
                .build();

        Engagement saved = engagementRepository.save(engagement);
        EngagementDto dto = toDto(saved);

        if (isNewInvite) {
            sendInvitation(client, engagement.getTitle());
        }

        projectActivityService.recordBestEffort(saved.getId(), currentUserProvider.get(),
                ProjectActivityType.ENGAGEMENT_CREATED, "Project created", null);

        return dto;
    }

    /**
     * Creates a placeholder account for a client the admin is granting project access to
     * before they've registered. Unusable random-hash password (same technique as
     * OAuth2LoginSuccessHandler#createUserFromGoogle — the column is NOT NULL) and
     * enabled=false so a password-login attempt is rejected outright by the authentication
     * manager until the client actually activates the invite.
     */
    private User inviteClient(String normalizedEmail, String name) {
        User user = User.builder()
                .fullName(name)
                .email(normalizedEmail)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.CLIENT)
                .enabled(false)
                .emailVerified(false)
                .invitationPending(true)
                .build();
        return userRepository.save(user);
    }

    private String resolveClientName(EngagementRequest request, Inquiry inquiry) {
        if (request.clientName() != null && !request.clientName().isBlank()) {
            return request.clientName().trim();
        }
        if (inquiry != null && inquiry.getName() != null && !inquiry.getName().isBlank()) {
            return inquiry.getName().trim();
        }
        // Last-resort fallback so fullName (NOT NULL) is never empty — the client can correct
        // this the moment they accept the invitation.
        String localPart = request.clientEmail().trim().split("@")[0];
        return localPart.isBlank() ? "there" : localPart;
    }

    private void sendInvitation(User client, String engagementTitle) {
        String token = oneTimeTokenService.issue(INVITE_NAMESPACE, client.getId().toString(), INVITE_TTL);
        String inviteUrl = frontendUrl + "/accept-invitation?token=" + token;
        emailService.sendClientInvitationEmail(client.getEmail(), client.getFullName(), engagementTitle, inviteUrl);
    }

    @Transactional(readOnly = true)
    public List<EngagementDto> listAllForAdmin() {
        return engagementRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<EngagementDto> listForCurrentClient() {
        User user = currentUserProvider.get();
        return engagementRepository.findByClientIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public EngagementDto get(UUID id) {
        return toDto(getEntityWithAccessCheck(id));
    }

    @Transactional
    public EngagementDto updateStatus(UUID id, EngagementStatus status) {
        Engagement engagement = engagementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Engagement not found: " + id));
        engagement.setStatus(status);
        EngagementDto dto = toDto(engagementRepository.save(engagement));

        projectActivityService.recordBestEffort(id, currentUserProvider.get(),
                ProjectActivityType.ENGAGEMENT_STATUS_CHANGED,
                "Project status changed to " + status.name().replace('_', ' '), null);

        return dto;
    }

    /**
     * Fetches an engagement, enforcing that the caller is either its client or an admin.
     * The transaction is deliberate: client is a LAZY association and the ownership check
     * must remain safe with spring.jpa.open-in-view=false.
     */
    @Transactional(readOnly = true)
    Engagement getEntityWithAccessCheck(UUID id) {
        Engagement engagement = engagementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Engagement not found: " + id));

        User current = currentUserProvider.get();
        boolean isOwner = engagement.getClient().getId().equals(current.getId());
        boolean isAdmin = current.getRole() == Role.ADMIN || current.getRole() == Role.SUPERADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have access to this engagement");
        }
        return engagement;
    }

    private EngagementDto toDto(Engagement e) {
        return EngagementDto.builder()
                .id(e.getId())
                .clientId(e.getClient().getId())
                .clientName(e.getClient().getFullName())
                .clientEmail(e.getClient().getEmail())
                .title(e.getTitle())
                .description(e.getDescription())
                .status(e.getStatus())
                .startDate(e.getStartDate())
                .targetEndDate(e.getTargetEndDate())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
