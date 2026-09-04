package com.neelastack.service;

import com.neelastack.dto.engagement.EngagementDto;
import com.neelastack.dto.engagement.EngagementRequest;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.EngagementStatus;
import com.neelastack.entity.Inquiry;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.EngagementRepository;
import com.neelastack.repository.InquiryRepository;
import com.neelastack.repository.UserRepository;
import com.neelastack.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EngagementService {

    private final EngagementRepository engagementRepository;
    private final UserRepository userRepository;
    private final InquiryRepository inquiryRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public EngagementDto create(EngagementRequest request) {
        User client = userRepository.findByEmail(request.clientEmail().toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No registered client account found for email: " + request.clientEmail() +
                                " — the client must sign up first."));

        Inquiry inquiry = request.inquiryId() != null
                ? inquiryRepository.findById(request.inquiryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found: " + request.inquiryId()))
                : null;

        Engagement engagement = Engagement.builder()
                .client(client)
                .inquiry(inquiry)
                .title(request.title())
                .description(request.description())
                .status(EngagementStatus.ONBOARDING)
                .startDate(request.startDate())
                .targetEndDate(request.targetEndDate())
                .build();

        return toDto(engagementRepository.save(engagement));
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
        return toDto(engagementRepository.save(engagement));
    }

    /**
     * Fetches an engagement, enforcing that the caller is either its client or an
     * admin.
     */
    Engagement getEntityWithAccessCheck(UUID id) {
        Engagement engagement = engagementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Engagement not found: " + id));

        User current = currentUserProvider.get();
        boolean isOwner = engagement.getClient().getId().equals(current.getId());
        boolean isAdmin = current.getRole() == Role.ADMIN;

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
}
