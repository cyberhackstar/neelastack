package com.neelastack.controller;

import com.neelastack.dto.engagement.EngagementDto;
import com.neelastack.dto.engagement.EngagementRequest;
import com.neelastack.dto.engagement.EngagementStatusUpdateRequest;
import com.neelastack.dto.engagement.MilestoneDto;
import com.neelastack.dto.engagement.MilestoneRequest;
import com.neelastack.dto.engagement.MilestoneStatusUpdateRequest;
import com.neelastack.service.EngagementService;
import com.neelastack.service.MilestoneService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/engagements")
@RequiredArgsConstructor
@Tag(name = "Admin — engagements", description = "Requires ROLE_ADMIN. The client must already have a registered account.")
public class AdminEngagementController {

    private final EngagementService engagementService;
    private final MilestoneService milestoneService;

    @GetMapping
    public List<EngagementDto> listAll() {
        return engagementService.listAllForAdmin();
    }

    @PostMapping
    public ResponseEntity<EngagementDto> create(@Valid @RequestBody EngagementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(engagementService.create(request));
    }

    @PatchMapping("/{id}/status")
    public EngagementDto updateStatus(@PathVariable UUID id, @Valid @RequestBody EngagementStatusUpdateRequest request) {
        return engagementService.updateStatus(id, request.status());
    }

    @PostMapping("/{id}/milestones")
    public ResponseEntity<MilestoneDto> addMilestone(@PathVariable UUID id, @Valid @RequestBody MilestoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.create(id, request));
    }

    @PatchMapping("/milestones/{milestoneId}/status")
    public MilestoneDto updateMilestoneStatus(@PathVariable UUID milestoneId, @Valid @RequestBody MilestoneStatusUpdateRequest request) {
        return milestoneService.updateStatus(milestoneId, request.status());
    }
}
