package br.com.arenadev.content.share;

import br.com.arenadev.content.LearningResourceFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Minha Biblioteca (ADR-0076 D8): items belong to the verified StudentAccount, with or without
 * Student/Enrollment. Every query is scoped by the server-derived account id (IDOR → 404).
 * Reading or saving never creates presence, XP, Journey progress or academic records.
 */
@Service
public class StudentLibraryService {
    private final JdbcTemplate jdbc;
    private final LibraryShareService shares;

    public StudentLibraryService(JdbcTemplate jdbc, LibraryShareService shares) {
        this.jdbc = jdbc;
        this.shares = shares;
    }

    @Transactional(readOnly = true)
    public List<ItemSummary> list(UUID accountId) {
        return jdbc.query("""
                select i.id, i.saved_at, i.updated_at, s.title, s.summary, s.format, s.revision,
                       s.source_url is not null as external_reference
                  from student_library_items i
                  join learning_resource_snapshots s on s.id = i.snapshot_id
                 where i.account_id = ?
                 order by i.saved_at desc, i.id
                """, (rs, row) -> new ItemSummary(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("summary"), LearningResourceFormat.valueOf(rs.getString("format")),
                rs.getInt("revision"), rs.getBoolean("external_reference"),
                rs.getTimestamp("saved_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), accountId);
    }

    @Transactional(readOnly = true)
    public ItemContent get(UUID accountId, UUID itemId) {
        var rows = jdbc.query("""
                select i.id, i.snapshot_id, i.saved_at from student_library_items i
                 where i.id = ? and i.account_id = ?
                """, (rs, row) -> new Object[]{rs.getObject("snapshot_id", UUID.class),
                rs.getTimestamp("saved_at").toInstant()}, itemId, accountId);
        if (rows.isEmpty()) throw notFound();
        var snapshot = shares.snapshot((UUID) rows.getFirst()[0]);
        return new ItemContent(itemId, snapshot.title(), snapshot.summary(), snapshot.format(), snapshot.body(),
                snapshot.sourceUrl(), snapshot.sourceLabel(), snapshot.revision(), snapshot.copyProtected(),
                snapshot.sourceUrl() != null, (Instant) rows.getFirst()[1]);
    }

    /** Removes the item from the collection only; the teacher's resource and snapshots stay. */
    @Transactional
    public void remove(UUID accountId, UUID itemId) {
        if (jdbc.update("delete from student_library_items where id = ? and account_id = ?", itemId, accountId) != 1) {
            throw notFound();
        }
    }

    private static LibraryAccessException notFound() {
        return new LibraryAccessException(HttpStatus.NOT_FOUND, LibraryAccessException.Code.ITEM_NOT_FOUND,
                "Material não encontrado na sua biblioteca.");
    }

    public record ItemSummary(UUID id, String title, String summary, LearningResourceFormat format, int revision,
                              boolean externalReference, Instant savedAt, Instant updatedAt) { }

    public record ItemContent(UUID id, String title, String summary, LearningResourceFormat format, String body,
                              String sourceUrl, String sourceLabel, int revision, boolean copyProtected,
                              boolean externalReference, Instant savedAt) { }
}
