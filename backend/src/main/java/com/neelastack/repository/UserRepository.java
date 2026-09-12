package com.neelastack.repository;

import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // Existence-only query so AdminBootstrapRunner doesn't load the entire users table on
    // every application startup just to answer "does an admin exist?".
    boolean existsByRole(Role role);

    // Backs the assignee picker for project tasks (Section 12/15 of the client-workspace
    // review) — staff-only, ordered by name so the dropdown reads sensibly without the
    // frontend having to sort it.
    List<User> findByRoleInOrderByFullNameAsc(List<Role> roles);
}

