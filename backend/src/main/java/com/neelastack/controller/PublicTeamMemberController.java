package com.neelastack.controller;

import com.neelastack.dto.content.TeamMemberDto;
import com.neelastack.service.TeamMemberService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/team-members")
@RequiredArgsConstructor
@Tag(name = "Public team", description = "Published Neelastack team members")
public class PublicTeamMemberController {
    private final TeamMemberService service;

    @GetMapping
    public List<TeamMemberDto> list() { return service.listPublic(); }
}
