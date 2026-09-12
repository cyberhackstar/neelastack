package com.neelastack.controller;

import com.neelastack.dto.engagement.AdminStaffDto;
import com.neelastack.dto.engagement.AdminStaffInviteRequest;
import com.neelastack.dto.engagement.AdminStaffUpdateRequest;
import com.neelastack.service.AdminStaffService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/staff-management")
@RequiredArgsConstructor
@Tag(name="Admin — staff management", description="SUPERADMIN-only staff administration")
public class AdminStaffController {
    private final AdminStaffService service;
    @GetMapping public List<AdminStaffDto> list(){ return service.list(); }
    @PostMapping("/invite") public ResponseEntity<AdminStaffDto> invite(@Valid @RequestBody AdminStaffInviteRequest request){ return ResponseEntity.status(HttpStatus.CREATED).body(service.invite(request)); }
    @PatchMapping("/{id}") public AdminStaffDto update(@PathVariable UUID id, @Valid @RequestBody AdminStaffUpdateRequest request){ return service.update(id, request); }
}
