package br.com.arenadev.journey;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Stores an immutable, transaction-bound snapshot of the complete old Journey,
 * its contents and all student progress before a destructive replacement.
 * Snapshot and replacement must succeed or roll back together.
 */
@Service
public class JourneyOverwriteSnapshotService {
    private final JdbcTemplate jdbc;

    public JourneyOverwriteSnapshotService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ProgressCounts(int enrollment, int redeemed) {
        public int total() { return enrollment + redeemed; }
    }

    public ProgressCounts counts(UUID journeyId) {
        return jdbc.queryForObject("""
                select
                  (select count(*) from journey_stage_progress p
                    join journey_stages s on s.id = p.journey_stage_id
                    where s.journey_id = ?) as enrollment_count,
                  (select count(*) from student_journey_stage_progress p
                    join journey_stages s on s.id = p.journey_stage_id
                    where s.journey_id = ?) as redeemed_count
                """,
                (rs, rowNum) -> new ProgressCounts(rs.getInt("enrollment_count"),
                        rs.getInt("redeemed_count")), journeyId, journeyId);
    }

    /**
     * With the stage rows already locked, lock existing progress records before
     * snapshotting so concurrent completions cannot be omitted from the backup.
     */
    public UUID capture(UUID journeyId) {
        jdbc.query("""
                select p.id from journey_stage_progress p
                join journey_stages s on s.id = p.journey_stage_id
                where s.journey_id = ? for update of p
                """, rs -> { }, journeyId);
        jdbc.query("""
                select p.id from student_journey_stage_progress p
                join journey_stages s on s.id = p.journey_stage_id
                where s.journey_id = ? for update of p
                """, rs -> { }, journeyId);

        UUID backupId = UUID.randomUUID();
        int inserted = jdbc.update("""
                insert into journey_overwrite_snapshots (
                  id, journey_id, journey_title, journey_status, journey_updated_at,
                  previous_stage_count, enrollment_progress_count, redeemed_progress_count, snapshot
                )
                select ?, j.id, j.title, j.status, j.updated_at,
                  (select count(*) from journey_stages s where s.journey_id = j.id),
                  (select count(*) from journey_stage_progress p
                    join journey_stages s on s.id = p.journey_stage_id where s.journey_id = j.id),
                  (select count(*) from student_journey_stage_progress p
                    join journey_stages s on s.id = p.journey_stage_id where s.journey_id = j.id),
                  jsonb_build_object(
                    'journey', to_jsonb(j),
                    'sections', coalesce((select jsonb_agg(to_jsonb(sec) order by sec.position)
                      from journey_sections sec where sec.journey_id = j.id), '[]'::jsonb),
                    'stages', coalesce((select jsonb_agg(to_jsonb(s) order by s.position)
                      from journey_stages s where s.journey_id = j.id), '[]'::jsonb),
                    'linkedResources', coalesce((select jsonb_agg(to_jsonb(lr))
                      from learning_resources lr where lr.id in
                      (select distinct s.learning_resource_id from journey_stages s
                       where s.journey_id = j.id and s.learning_resource_id is not null)), '[]'::jsonb),
                    'enrollmentProgress', coalesce((select jsonb_agg(to_jsonb(p))
                      from journey_stage_progress p join journey_stages s
                      on s.id = p.journey_stage_id where s.journey_id = j.id), '[]'::jsonb),
                    'redeemedProgress', coalesce((select jsonb_agg(to_jsonb(p))
                      from student_journey_stage_progress p join journey_stages s
                      on s.id = p.journey_stage_id where s.journey_id = j.id), '[]'::jsonb)
                  )
                from journeys j where j.id = ?
                """, backupId, journeyId);
        if (inserted != 1) throw new IllegalStateException("Falha ao salvar cópia de segurança da jornada.");
        return backupId;
    }
}
