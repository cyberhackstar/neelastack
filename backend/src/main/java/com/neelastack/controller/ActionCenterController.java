package com.neelastack.controller;

import com.neelastack.dto.actioncenter.ActionItemDto;
import com.neelastack.service.ActionCenterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Authenticated — ownership enforced inside ActionCenterService via
 *  EngagementService#getEntityWithAccessCheck. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Client action-required center")
public class ActionCenterController {

    private final ActionCenterService actionCenterService;

    @GetMapping("/api/v1/engagements/{engagementId}/action-items")
    public List<ActionItemDto> actionItems(@PathVariable UUID engagementId) {
        return actionCenterService.forEngagement(engagementId);
    }
}
