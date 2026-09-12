package com.neelastack.service;

import com.neelastack.dto.engagement.StaffSummaryDto;
import com.neelastack.entity.Role;
import com.neelastack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffDirectoryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<StaffSummaryDto> listStaff() {
        return userRepository.findByRoleInOrderByFullNameAsc(List.of(Role.ADMIN, Role.SUPERADMIN))
                .stream()
                .map(u -> StaffSummaryDto.builder().id(u.getId()).fullName(u.getFullName()).email(u.getEmail()).build())
                .toList();
    }
}
