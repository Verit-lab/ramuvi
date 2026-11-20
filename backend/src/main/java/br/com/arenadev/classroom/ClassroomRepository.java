package br.com.arenadev.classroom;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {
    List<Classroom> findAllByOrderByNameAsc();
    List<Classroom> findByActiveTrueOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    /** Serializes session lifecycle changes of ONE classroom (start/switch), ADR-0076 D3. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select classroom from Classroom classroom where classroom.id = :id")
    Optional<Classroom> findByIdForUpdate(@Param("id") UUID id);
}
