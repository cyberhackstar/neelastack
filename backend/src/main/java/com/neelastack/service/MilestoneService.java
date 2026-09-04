package com.neelastack.service;

import com.neelastack.dto.engagement.MilestoneDto;
import com.neelastack.dto.engagement.MilestoneRequest;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.Milestone;
import com.neelastack.entity.MilestoneStatus;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.MilestoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final EngagementService engagementService;

    public List<MilestoneDto> list(UUID engagementId) {
        // Access check: throws if the caller can't see this engagement
        engagementService.getEntityWithAccessCheck(engagementId);
        return milestoneRepository.findByEngagementIdOrderByDisplayOrderAsc(engagementId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public MilestoneDto create(UUID engagementId, MilestoneRequest request) {
        Engagement engagement = engagementService.getEntityWithAccessCheck(engagementId);

        Milestone milestone = Milestone.builder()
                .engagement(engagement)
                .title(request.title())
                .description(request.description())
                .dueDate(request.dueDate())
                .status(MilestoneStatus.PENDING)
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .build();

        return toDto(milestoneRepository.save(milestone));
    }

    @Transactional
    public MilestoneDto updateStatus(UUID milestoneId, MilestoneStatus status) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + milestoneId));
        milestone.setStatus(status);
        return toDto(milestoneRepository.save(milestone));
    }

    private MilestoneDto toDto(Milestone m) {
        return MilestoneDto.builder()
                .id(m.getId())
                .engagementId(m.getEngagement().getId())
                .title(m.getTitle())
                .description(m.getDescription())
                .status(m.getStatus())
                .dueDate(m.getDueDate())
                .displayOrder(m.getDisplayOrder())
                .build();
    }
}
