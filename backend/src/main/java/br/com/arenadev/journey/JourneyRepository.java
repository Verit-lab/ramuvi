package br.com.arenadev.journey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JourneyRepository extends JpaRepository<Journey, UUID> {
    List<Journey> findByClassroomIdOrderByUpdatedAtDesc(UUID classroomId);
    List<Journey> findByClassroomIdAndStatusOrderByUpdatedAtDesc(UUID classroomId, JourneyStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select journey from Journey journey where journey.id = :id")
    Optional<Journey> lockForOverwrite(@Param("id") UUID id);

    @Query("""
            select distinct journey from Journey journey
            join journey.stages stage
            where stage.learningResource.id = :resourceId
            order by journey.updatedAt desc
            """)
    List<Journey> findByLearningResourceId(@Param("resourceId") UUID resourceId);
}
