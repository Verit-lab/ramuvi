package br.com.arenadev.journey;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JourneyOverwriteSnapshotServiceTest {
    @Test
    void capturesBothProgressSourcesAndOldStageBodiesInSingleSnapshot() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JourneyOverwriteSnapshotService service = new JourneyOverwriteSnapshotService(jdbc);
        UUID journeyId = UUID.randomUUID();
        when(jdbc.update(anyString(), any(), eq(journeyId))).thenReturn(1);

        UUID snapshotId = service.capture(journeyId);

        assertThat(snapshotId).isNotNull();
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq(snapshotId), eq(journeyId));
        assertThat(sql.getValue()).contains(
                "journey_overwrite_snapshots", "to_jsonb(j)", "to_jsonb(sec)",
                "to_jsonb(s)", "journey_stage_progress", "student_journey_stage_progress",
                "enrollmentProgress", "redeemedProgress", "linkedResources");
        verify(jdbc, times(2)).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class), eq(journeyId));
    }

    @Test
    void refusesOverwriteWhenBackupCannotBePersisted() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JourneyOverwriteSnapshotService service = new JourneyOverwriteSnapshotService(jdbc);
        UUID journeyId = UUID.randomUUID();
        assertThatThrownBy(() -> service.capture(journeyId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cópia de segurança");
    }
}
