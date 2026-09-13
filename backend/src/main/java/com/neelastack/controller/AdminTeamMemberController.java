package com.neelastack.controller;

import com.neelastack.dto.content.TeamMemberDto;
import com.neelastack.service.TeamMemberService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/team-members")
@RequiredArgsConstructor
@Tag(name = "Admin — team members", description = "SUPERADMIN-only public team member CMS")
public class AdminTeamMemberController {
    private final TeamMemberService service;

    @GetMapping
    public List<TeamMemberDto> list() { return service.listAdmin(); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TeamMemberDto> create(
            @RequestParam String name,
            @RequestParam String role,
            @RequestParam String bio,
            @RequestParam(required = false, defaultValue = "") String skills,
            @RequestParam(required = false, defaultValue = "0") Integer sortOrder,
            @RequestParam(required = false, defaultValue = "true") Boolean active,
            @RequestPart(required = false) MultipartFile photo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(name, role, bio, skills, sortOrder, active, photo));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TeamMemberDto update(
            @PathVariable UUID id,
            @RequestParam String name,
            @RequestParam String role,
            @RequestParam String bio,
            @RequestParam(required = false, defaultValue = "") String skills,
            @RequestParam(required = false, defaultValue = "0") Integer sortOrder,
            @RequestParam(required = false, defaultValue = "true") Boolean active,
            @RequestPart(required = false) MultipartFile photo) {
        return service.update(id, name, role, bio, skills, sortOrder, active, photo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
