package com.neelastack.controller;

import com.neelastack.dto.health.ProjectHealthDto;
import com.neelastack.dto.health.ProjectOperationsSummaryDto;
import com.neelastack.service.ProjectHealthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * P0 #5 — health is exposed under both a shared authenticated path (so a client can see
 * their own project's status) and an admin-only rollup for the cross-project command center.
 * A single client-visible endpoint is deliberately reused for both admin and client callers:
 * ProjectHealthService itself enforces ownership via EngagementService#getEntityWithAccessCheck,
 * exactly like every other engagement-scoped read in this codebase.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Project health")
public class ProjectHealthController {

    private final ProjectHealthService projectHealthService;

    @GetMapping("/api/v1/engagements/{engagementId}/health")
    public ProjectHealthDto health(@PathVariable UUID engagementId) {
        return projectHealthService.computeForEngagement(engagementId);
    }

    @GetMapping("/api/v1/admin/project-operations/summary")
    public ProjectOperationsSummaryDto operationsSummary() {
        return projectHealthService.summary();
    }
}
