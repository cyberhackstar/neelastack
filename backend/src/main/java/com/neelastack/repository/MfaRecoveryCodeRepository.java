package com.neelastack.repository;

import com.neelastack.entity.MfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {
    List<MfaRecoveryCode> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    /**
     * Atomic, single-statement conditional delete used by MfaService#consumeRecoveryCode to
     * close a race: two concurrent requests presenting the same still-unused recovery code
     * previously could both load it, both pass the bcrypt match, and both "succeed" before
     * either row was deleted. A Postgres DELETE ... WHERE id = ? is a single atomic statement,
     * so only one of two concurrent callers can ever see modifiedCount == 1 for the same id;
     * the loser sees 0 and must treat the code as already consumed.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM MfaRecoveryCode c WHERE c.id = :id")
    int deleteByIdAtomic(@org.springframework.data.repository.query.Param("id") UUID id);
}
