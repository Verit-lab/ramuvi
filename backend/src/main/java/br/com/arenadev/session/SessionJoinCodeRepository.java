package br.com.arenadev.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionJoinCodeRepository extends JpaRepository<SessionJoinCode, UUID> {
    Optional<SessionJoinCode> findByCodeIgnoreCaseAndActiveTrue(String code);
    Optional<SessionJoinCode> findBySessionId(UUID sessionId);
    List<SessionJoinCode> findBySessionClassroomIdInAndActiveTrueOrderBySessionStartedAtDesc(List<UUID> classroomIds);
    Optional<SessionJoinCode> findBySessionIdAndActiveTrue(UUID sessionId);
    boolean existsByCodeIgnoreCase(String code);

    /** Row lock (FOR UPDATE) so concurrent renewals of an expired code rotate it only once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select code from SessionJoinCode code where code.session.id = :sessionId")
    Optional<SessionJoinCode> findBySessionIdForUpdate(@Param("sessionId") UUID sessionId);
}
