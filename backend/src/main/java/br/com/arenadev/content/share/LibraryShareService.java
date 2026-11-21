package br.com.arenadev.content.share;

import br.com.arenadev.content.LearningResource;
import br.com.arenadev.content.LearningResourceFormat;
import br.com.arenadev.content.LearningResourceRepository;
import br.com.arenadev.shared.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * ADR-0076 D7/D9: a teacher distributes a Library resource without a classroom. Each link points
 * to an IMMUTABLE snapshot of the authorized representation; later editorial edits never change
 * what the link (or an item saved from it) delivers. Tokens have 160 random bits, are shown once
 * and stored only as SHA-256. Links carry aggregate counters only, never readers.
 */
@Service
public class LibraryShareService {
    static final int MIN_DAYS = 1;
    static final int MAX_DAYS = 90;
    static final int MAX_OPEN_LINKS_PER_RESOURCE = 20;
    static final String TOKEN_PATTERN = "[A-Z2-7]{32}";
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    public static final String SHARE_PATH_PREFIX = "/learn/library/shared#acesso=";

    public enum Permission { VIEW, SAVE }

    private final JdbcTemplate jdbc;
    private final LearningResourceRepository resources;
    private final SecureRandom random = new SecureRandom();

    public LibraryShareService(JdbcTemplate jdbc, LearningResourceRepository resources) {
        this.jdbc = jdbc;
        this.resources = resources;
    }

    // ------------------------------------------------------------------ teacher

    @Transactional
    public IssuedShare create(UUID resourceId, Permission permission, int validDays, String label) {
        Objects.requireNonNull(permission, "permission");
        if (validDays < MIN_DAYS || validDays > MAX_DAYS) {
            throw new IllegalArgumentException("A validade do link deve ficar entre 1 e 90 dias.");
        }
        String cleanLabel = label == null || label.isBlank() ? null : label.trim();
        if (cleanLabel != null && cleanLabel.length() > 120) {
            throw new IllegalArgumentException("A identificação do link deve ter no máximo 120 caracteres.");
        }
        // Lock the resource row: serializes snapshot creation and the open-links cap.
        jdbc.query("select id from learning_resources where id = ? for update",
                (rs, row) -> rs.getObject("id", UUID.class), resourceId);
        LearningResource resource = resources.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        if (resource.getArchivedAt() != null) {
            throw new LibraryAccessException(HttpStatus.CONFLICT, LibraryAccessException.Code.RESOURCE_NOT_SHAREABLE,
                    "Restaure o recurso arquivado antes de compartilhá-lo.");
        }
        Integer open = jdbc.queryForObject("""
                select count(*) from library_share_links
                 where source_resource_id = ? and revoked_at is null and expires_at > now()
                """, Integer.class, resourceId);
        if (open != null && open >= MAX_OPEN_LINKS_PER_RESOURCE) {
            throw new LibraryAccessException(HttpStatus.CONFLICT, LibraryAccessException.Code.TOO_MANY_OPEN_SHARES,
                    "Este recurso já tem muitos links ativos. Revogue algum antes de criar outro.");
        }
        UUID snapshotId = ensureSnapshot(resource);
        byte[] secret = new byte[20];
        random.nextBytes(secret);
        String token = base32(secret);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofDays(validDays));
        jdbc.update("""
                insert into library_share_links
                    (id, resource_id, source_resource_id, snapshot_id, token_hash, permission, label, created_at, expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, resourceId, resourceId, snapshotId, digest(token), permission.name(), cleanLabel,
                Timestamp.from(now), Timestamp.from(expiresAt));
        return new IssuedShare(id, permission, cleanLabel, now, expiresAt, resource.getRevision(), token,
                SHARE_PATH_PREFIX + token);
    }

    @Transactional(readOnly = true)
    public List<ShareView> list(UUID resourceId) {
        if (!resources.existsById(resourceId)) throw new ResourceNotFoundException("Recurso não encontrado.");
        return jdbc.query("""
                select l.id, l.permission, l.label, l.created_at, l.expires_at, l.revoked_at,
                       l.view_count, l.save_count, l.last_used_at, s.revision
                  from library_share_links l
                  join learning_resource_snapshots s on s.id = l.snapshot_id
                 where l.source_resource_id = ?
                 order by l.created_at desc
                """, (rs, row) -> {
            Timestamp revoked = rs.getTimestamp("revoked_at");
            Timestamp lastUsed = rs.getTimestamp("last_used_at");
            Instant expiresAt = rs.getTimestamp("expires_at").toInstant();
            String status = revoked != null ? "REVOKED" : expiresAt.isAfter(Instant.now()) ? "ACTIVE" : "EXPIRED";
            return new ShareView(rs.getObject("id", UUID.class), Permission.valueOf(rs.getString("permission")),
                    rs.getString("label"), rs.getTimestamp("created_at").toInstant(), expiresAt,
                    revoked == null ? null : revoked.toInstant(), status, rs.getInt("revision"),
                    rs.getInt("view_count"), rs.getInt("save_count"),
                    lastUsed == null ? null : lastUsed.toInstant());
        }, resourceId);
    }

    /** Stops NEW views and saves through this link; items already saved stay (ADR-0076 D7). */
    @Transactional
    public void revoke(UUID shareId) {
        int changed = jdbc.update("update library_share_links set revoked_at = now() where id = ? and revoked_at is null",
                shareId);
        if (changed == 0) {
            Integer exists = jdbc.queryForObject("select count(*) from library_share_links where id = ?",
                    Integer.class, shareId);
            if (exists == null || exists == 0) throw new ResourceNotFoundException("Link não encontrado.");
        }
    }

    // ------------------------------------------------------------------ public / student

    /**
     * VIEW links open anonymously. SAVE links require a verified account (fail closed): the
     * token alone never reveals SAVE material. accountId is server-derived, never from the body.
     */
    @Transactional
    public SharedContent preview(String rawToken, UUID accountId) {
        LinkRow link = lockUsable(rawToken);
        if (link.permission() == Permission.SAVE && accountId == null) {
            throw new LibraryAccessException(HttpStatus.UNAUTHORIZED, LibraryAccessException.Code.ACCOUNT_REQUIRED,
                    "Entre com sua conta de estudante para abrir e salvar este material.");
        }
        jdbc.update("update library_share_links set view_count = view_count + 1, last_used_at = now() where id = ?",
                link.id());
        String savedSnapshot = accountId == null ? null : jdbc.query("""
                select snapshot_id from student_library_items where account_id = ? and source_resource_id = ?
                """, (rs, row) -> rs.getString("snapshot_id"), accountId, link.sourceResourceId())
                .stream().findFirst().orElse(null);
        return SharedContent.of(snapshot(link.snapshotId()), link.permission(), link.expiresAt(),
                savedSnapshot != null, savedSnapshot != null && !savedSnapshot.equals(link.snapshotId().toString()));
    }

    /** Idempotent: one item per (account, resource). A different version replaces only on request. */
    @Transactional
    public SaveResult save(String rawToken, UUID accountId, boolean replaceVersion) {
        Objects.requireNonNull(accountId, "accountId");
        LinkRow link = lockUsable(rawToken);
        if (link.permission() != Permission.SAVE) {
            throw new LibraryAccessException(HttpStatus.FORBIDDEN, LibraryAccessException.Code.VIEW_ONLY,
                    "Este link permite apenas visualizar o material.");
        }
        int inserted = jdbc.update("""
                insert into student_library_items (id, account_id, source_resource_id, snapshot_id, share_link_id)
                values (?, ?, ?, ?, ?)
                on conflict (account_id, source_resource_id) do nothing
                """, UUID.randomUUID(), accountId, link.sourceResourceId(), link.snapshotId(), link.id());
        var item = jdbc.query("""
                select id, snapshot_id from student_library_items
                 where account_id = ? and source_resource_id = ? for update
                """, (rs, row) -> new Object[]{rs.getObject("id", UUID.class), rs.getObject("snapshot_id", UUID.class)},
                accountId, link.sourceResourceId()).getFirst();
        UUID itemId = (UUID) item[0];
        UUID currentSnapshot = (UUID) item[1];
        String status;
        boolean newer = false;
        if (inserted == 1) {
            status = "SAVED";
            jdbc.update("update library_share_links set save_count = save_count + 1, last_used_at = now() where id = ?",
                    link.id());
        } else if (currentSnapshot.equals(link.snapshotId())) {
            status = "ALREADY_SAVED";
        } else if (replaceVersion) {
            jdbc.update("""
                    update student_library_items set snapshot_id = ?, share_link_id = ?, updated_at = now()
                     where id = ?
                    """, link.snapshotId(), link.id(), itemId);
            status = "UPDATED";
        } else {
            status = "ALREADY_SAVED";
            newer = true;
        }
        return new SaveResult(itemId, status, newer);
    }

    // ------------------------------------------------------------------ internals

    private LinkRow lockUsable(String rawToken) {
        String token = normalizeToken(rawToken);
        if (token == null) throw new IllegalArgumentException("Link de material inválido.");
        var rows = jdbc.query("""
                select id, source_resource_id, snapshot_id, permission, expires_at
                  from library_share_links
                 where token_hash = ? and revoked_at is null and expires_at > now() and resource_id is not null
                 for update
                """, ps -> ps.setBytes(1, digest(token)), (rs, row) -> new LinkRow(
                rs.getObject("id", UUID.class), rs.getObject("source_resource_id", UUID.class),
                rs.getObject("snapshot_id", UUID.class), Permission.valueOf(rs.getString("permission")),
                rs.getTimestamp("expires_at").toInstant()));
        if (rows.size() != 1) throw LibraryAccessException.unavailable();
        return rows.getFirst();
    }

    public SnapshotContent snapshot(UUID snapshotId) {
        return jdbc.queryForObject("""
                select id, source_resource_id, revision, format, title, summary, body, source_url, source_label,
                       copy_protected, content_sha256, created_at
                  from learning_resource_snapshots where id = ?
                """, (rs, row) -> new SnapshotContent(rs.getObject("id", UUID.class),
                rs.getObject("source_resource_id", UUID.class), rs.getInt("revision"),
                LearningResourceFormat.valueOf(rs.getString("format")), rs.getString("title"),
                rs.getString("summary"), rs.getString("body"), rs.getString("source_url"),
                rs.getString("source_label"), rs.getBoolean("copy_protected"), rs.getString("content_sha256"),
                rs.getTimestamp("created_at").toInstant()), snapshotId);
    }

    UUID ensureSnapshot(LearningResource resource) {
        String url = safeExternalUrl(resource.getSourceUrl());
        if (resource.getSourceUrl() != null && !resource.getSourceUrl().isBlank() && url == null) {
            throw new LibraryAccessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    LibraryAccessException.Code.RESOURCE_NOT_SHAREABLE,
                    "O endereço externo do recurso precisa usar http ou https para ser compartilhado.");
        }
        boolean hasBody = resource.getBody() != null && !resource.getBody().isBlank();
        if (resource.getFormat() == LearningResourceFormat.LINK ? url == null : !hasBody && url == null) {
            throw new LibraryAccessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    LibraryAccessException.Code.RESOURCE_NOT_SHAREABLE,
                    "O recurso não tem conteúdo para compartilhar.");
        }
        String sha = checksum(resource, url);
        var existing = jdbc.query("""
                select id from learning_resource_snapshots
                 where source_resource_id = ? and revision = ? and content_sha256 = ?
                """, (rs, row) -> rs.getObject("id", UUID.class), resource.getId(), resource.getRevision(), sha);
        if (!existing.isEmpty()) return existing.getFirst();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into learning_resource_snapshots
                    (id, resource_id, source_resource_id, revision, format, title, summary, body,
                     source_url, source_label, copy_protected, content_sha256)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (source_resource_id, revision, content_sha256) do nothing
                """, id, resource.getId(), resource.getId(), resource.getRevision(), resource.getFormat().name(),
                resource.getTitle(), resource.getSummary(), resource.getBody(), url, resource.getSourceLabel(),
                resource.isCopyProtected(), sha);
        return jdbc.queryForObject("""
                select id from learning_resource_snapshots
                 where source_resource_id = ? and revision = ? and content_sha256 = ?
                """, UUID.class, resource.getId(), resource.getRevision(), sha);
    }

    static String safeExternalUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !scheme.equals("http")) return null;
            if (uri.getHost() == null || uri.getUserInfo() != null) return null;
            return uri.toString();
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String checksum(LearningResource resource, String url) {
        String canonical = String.join("\u0000",
                resource.getFormat().name(), nz(resource.getTitle()), nz(resource.getSummary()), nz(resource.getBody()),
                nz(url), nz(resource.getSourceLabel()), Boolean.toString(resource.isCopyProtected()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível", impossible);
        }
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    /** Accepts the token as typed/scanned: lowercase, spaces or hyphens between groups. */
    public static String normalizeToken(String raw) {
        if (raw == null || raw.length() > 64) return null;
        String compact = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        return compact.matches(TOKEN_PATTERN) ? compact : null;
    }

    static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) out.append(BASE32[(buffer << (5 - bits)) & 31]);
        return out.toString();
    }

    static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível", impossible);
        }
    }

    private record LinkRow(UUID id, UUID sourceResourceId, UUID snapshotId, Permission permission, Instant expiresAt) { }

    /** Returned ONLY by create, with Cache-Control: no-store. */
    public record IssuedShare(UUID id, Permission permission, String label, Instant createdAt, Instant expiresAt,
                              int revision, String token, String path) { }

    /** Teacher listing: state and aggregate counters, never the token nor who read/saved. */
    public record ShareView(UUID id, Permission permission, String label, Instant createdAt, Instant expiresAt,
                            Instant revokedAt, String status, int revision, int viewCount, int saveCount,
                            Instant lastUsedAt) { }

    public record SnapshotContent(UUID id, UUID sourceResourceId, int revision, LearningResourceFormat format,
                                  String title, String summary, String body, String sourceUrl, String sourceLabel,
                                  boolean copyProtected, String contentSha256, Instant createdAt) { }

    /** Public preview: material only; no teacher, classroom, student or academic data. */
    public record SharedContent(String title, String summary, LearningResourceFormat format, String body,
                                String sourceUrl, String sourceLabel, int revision, boolean copyProtected,
                                boolean externalReference, Permission permission, Instant expiresAt,
                                boolean canSave, boolean alreadySaved, boolean savedVersionDiffers) {
        static SharedContent of(SnapshotContent snapshot, Permission permission, Instant expiresAt,
                                boolean alreadySaved, boolean savedVersionDiffers) {
            return new SharedContent(snapshot.title(), snapshot.summary(), snapshot.format(), snapshot.body(),
                    snapshot.sourceUrl(), snapshot.sourceLabel(), snapshot.revision(), snapshot.copyProtected(),
                    snapshot.sourceUrl() != null, permission, expiresAt, permission == Permission.SAVE,
                    alreadySaved, savedVersionDiffers);
        }
    }

    public record SaveResult(UUID itemId, String status, boolean newerVersionAvailable) { }
}
