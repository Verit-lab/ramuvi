package br.com.arenadev.journey;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Locks removed stage rows until the progress check and deletion commit. */
public interface JourneyStageRepository extends JpaRepository<JourneyStage, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from JourneyStage s where s.id in :stageIds")
    List<JourneyStage> lockForReconstruction(@Param("stageIds") Collection<UUID> stageIds);
}
