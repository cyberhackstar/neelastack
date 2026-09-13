package com.neelastack.repository;

import com.neelastack.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    List<TeamMember> findByActiveTrueOrderBySortOrderAscNameAsc();
    List<TeamMember> findAllByOrderBySortOrderAscNameAsc();
}
