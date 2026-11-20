package br.com.arenadev.identity.account;

import br.com.arenadev.classroom.ClassroomService;
import br.com.arenadev.classroom.StudentAnonymizationService;
import br.com.arenadev.classroom.StudentService;
import br.com.arenadev.content.LearningResourceFormat;
import br.com.arenadev.content.LearningResourceService;
import br.com.arenadev.content.LearningResourceSourceType;
import br.com.arenadev.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v0.12.2 I3a/I3b (ADR-0076 D7–D9) over HTTP + PostgreSQL 17: teacher shares Library resources
 * without a classroom; VIEW opens anonymously, SAVE needs a verified account; immutable
 * snapshots; Minha Biblioteca per StudentAccount without Student/Enrollment; revocation,
 * expiry, IDOR, hard delete of the classroom and LGPD anonymization.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.library.sharing.enabled=true",
        "app.library.personal.enabled=true",
        "app.student-identity.oidc.enabled=true",
        "app.student-identity.enrollment-claim-enabled=true",
        "app.student-identity.oidc.google-client-id=unused",
        "app.student-identity.oidc.google-client-secret=unused",
        "app.student-identity.oidc.public-base-url=http://localhost:3000",
        "app.security.join-rate-limit.max-failures=500",
        "app.teacher.username=" + OidcHttpSupport.TEACHER_USER,
        "app.teacher.password=" + OidcHttpSupport.TEACHER_PASSWORD
})
@Import(OidcHttpSupport.FakeProviderRegistrations.class)
class LibraryShareHttpIT extends OidcHttpSupport {
    private static final String ITEMS = "/api/student-account/library/items";
    private static final String PREVIEW = "/api/library/shares/preview";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, LibraryShareHttpIT.class);
    }

    @LocalServerPort private int port;
    @Autowired private LearningResourceService resources;
    @Autowired private ClassroomService classrooms;
    @Autowired private StudentService students;
    @Autowired private StudentAnonymizationService anonymization;
    @Autowired private JdbcTemplate jdbc;

    @Override int port() { return port; }

    // ------------------------------------------------------------- I3a

    @Test
    void teacherSharesWithoutClassroomAndTheTokenIsShownOnceAndStoredOnlyAsHash() throws Exception {
        UUID resource = libraryResource("Guia de estudo", "# Guia\n\nConteúdo **seguro**.", LearningResourceFormat.MARKDOWN, null);
        String sharesPath = "/api/library/resources/" + resource + "/shares";
        var anonymous = new Browser(client(), null);
        assertThat(send(anonymous.client(), "POST", sharesPath, "{\"permission\":\"VIEW\",\"validDays\":7}",
                csrf(anonymous.client())).statusCode()).isEqualTo(401);
        var student = student("google", sub(), null, null, "Aluna");
        assertThat(post(student, sharesPath, "{\"permission\":\"VIEW\",\"validDays\":7}").statusCode()).isEqualTo(403);
        var teacher = teacher();
        assertThat(send(teacher.client(), "POST", sharesPath, "{\"permission\":\"VIEW\",\"validDays\":7}", null)
                .statusCode()).as("CSRF").isEqualTo(403);
        assertThat(post(teacher, sharesPath, "{\"permission\":\"VIEW\",\"validDays\":0}").statusCode()).isEqualTo(400);
        assertThat(post(teacher, sharesPath, "{\"permission\":\"VIEW\",\"validDays\":91}").statusCode()).isEqualTo(400);
        assertThat(post(teacher, sharesPath, "{\"permission\":\"ADMIN\",\"validDays\":7}").statusCode()).isEqualTo(400);

        var issued = post(teacher, sharesPath, "{\"permission\":\"VIEW\",\"validDays\":7,\"label\":\"Turma A\"}");
        assertThat(issued.statusCode()).as(issued.body()).isEqualTo(201);
        assertThat(issued.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        String token = field(issued.body(), "token");
        assertThat(token).matches("[A-Z2-7]{32}"); // 160 random bits
        assertThat(field(issued.body(), "path")).isEqualTo("/learn/library/shared#acesso=" + token);

        var listed = get(teacher, sharesPath);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body()).doesNotContain(token).contains("\"status\":\"ACTIVE\"").contains("\"viewCount\":0");
        assertThat(jdbc.queryForObject("select count(*) from library_share_links where encode(token_hash,'escape') like ?",
                Integer.class, "%" + token + "%")).isZero();
        assertThat(jdbc.queryForObject("select octet_length(token_hash) from library_share_links where source_resource_id = ?",
                Integer.class, resource)).isEqualTo(32);
        assertThat(get(student, sharesPath).statusCode()).isEqualTo(403);
    }

    @Test
    void anonymousViewOpensAnImmutableSnapshotOfEveryFormatWithoutAcademicData() throws Exception {
        var teacher = teacher();
        UUID markdown = libraryResource("Markdown", "# Título\n\n<script>alert(1)</script> texto v1", LearningResourceFormat.MARKDOWN, null);
        UUID plain = libraryResource("Texto", "Linha simples v1", LearningResourceFormat.PLAIN_TEXT, null);
        UUID link = libraryResource("Referência", null, LearningResourceFormat.LINK, "https://example.org/artigo");
        var anonymous = new Browser(client(), null);

        String markdownToken = share(teacher, markdown, "VIEW");
        var preview = send(anonymous.client(), "POST", PREVIEW, "{\"token\":\"" + markdownToken.toLowerCase() + "\"}", null);
        assertThat(preview.statusCode()).as(preview.body()).isEqualTo(200);
        assertThat(preview.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(preview.headers().firstValue("X-Robots-Tag").orElse("")).contains("noindex");
        assertThat(preview.headers().firstValue("Referrer-Policy").orElse("")).isEqualTo("no-referrer");
        assertThat(preview.body()).contains("texto v1").contains("\"format\":\"MARKDOWN\"")
                .contains("\"canSave\":false").doesNotContain("classroom").doesNotContain("student")
                .doesNotContain("score").doesNotContain(markdown.toString());

        assertThat(send(anonymous.client(), "POST", PREVIEW, "{\"token\":\"" + share(teacher, plain, "VIEW") + "\"}", null)
                .body()).contains("Linha simples v1").contains("\"format\":\"PLAIN_TEXT\"");
        var external = send(anonymous.client(), "POST", PREVIEW, "{\"token\":\"" + share(teacher, link, "VIEW") + "\"}", null);
        assertThat(external.body()).contains("\"format\":\"LINK\"").contains("https://example.org/artigo")
                .contains("\"externalReference\":true");

        // Editorial change after sharing: the link keeps delivering the snapshot it points to.
        resources.updateLibrary(markdown, input("Markdown", "# Título\n\ntexto v2", LearningResourceFormat.MARKDOWN, null));
        var after = send(anonymous.client(), "POST", PREVIEW, "{\"token\":\"" + markdownToken + "\"}", null);
        assertThat(after.body()).contains("texto v1").doesNotContain("texto v2");
        String newToken = share(teacher, markdown, "VIEW");
        assertThat(send(anonymous.client(), "POST", PREVIEW, "{\"token\":\"" + newToken + "\"}", null).body())
                .contains("texto v2");
        assertThat(jdbc.queryForObject("select count(*) from learning_resource_snapshots where source_resource_id = ?",
                Integer.class, markdown)).isEqualTo(2);
        assertThat(get(teacher, "/api/library/resources/" + markdown + "/shares").body()).contains("\"viewCount\":2");

        // A javascript: URL is never snapshotted as an external reference.
        UUID unsafe = libraryResource("Inseguro", "corpo", LearningResourceFormat.MARKDOWN, null);
        jdbc.update("update learning_resources set source_url = 'javascript:alert(1)' where id = ?", unsafe);
        assertThat(post(teacher, "/api/library/resources/" + unsafe + "/shares",
                "{\"permission\":\"VIEW\",\"validDays\":7}").statusCode()).isEqualTo(422);
    }

    @Test
    void saveLinkNeedsAVerifiedAccountAndViewLinkNeverSaves() throws Exception {
        var teacher = teacher();
        UUID resource = libraryResource("Salvável", "conteúdo para salvar", LearningResourceFormat.MARKDOWN, null);
        String saveToken = share(teacher, resource, "SAVE");
        String viewToken = share(teacher, resource, "VIEW");
        var anonymous = new Browser(client(), null);
        var denied = send(anonymous.client(), "POST", PREVIEW, "{\"token\":\"" + saveToken + "\"}", null);
        assertThat(denied.statusCode()).isEqualTo(401);
        assertThat(field(denied.body(), "code")).isEqualTo("ACCOUNT_REQUIRED");
        assertThat(denied.body()).doesNotContain("conteúdo para salvar");
        assertThat(send(anonymous.client(), "POST", ITEMS, "{\"token\":\"" + saveToken + "\"}",
                csrf(anonymous.client())).statusCode()).isEqualTo(401);

        var student = student("google", sub(), null, null, "Sem turma");
        assertThat(get(student, "/api/student-account/session").body()).contains("\"studentId\":null");
        var view = post(student, ITEMS, "{\"token\":\"" + viewToken + "\"}");
        assertThat(view.statusCode()).isEqualTo(403);
        assertThat(field(view.body(), "code")).isEqualTo("VIEW_ONLY");
        assertThat(send(student.client(), "POST", ITEMS, "{\"token\":\"" + saveToken + "\"}", null).statusCode())
                .as("CSRF").isEqualTo(403);
        var previewed = post(student, PREVIEW, "{\"token\":\"" + saveToken + "\"}");
        assertThat(previewed.statusCode()).isEqualTo(200);
        assertThat(previewed.body()).contains("\"canSave\":true").contains("\"alreadySaved\":false");
    }

    // ------------------------------------------------------------- I3b

    @Test
    void accountWithoutEnrollmentSavesIdempotentlyAndReadsOnAnotherDevice() throws Exception {
        var teacher = teacher();
        UUID first = libraryResource("Material A", "texto A", LearningResourceFormat.MARKDOWN, null);
        UUID second = libraryResource("Material B", "texto B", LearningResourceFormat.PLAIN_TEXT, null);
        String tokenA = share(teacher, first, "SAVE");
        String tokenB = share(teacher, second, "SAVE");
        int studentsBefore = count("students");
        int enrollmentsBefore = count("enrollments");
        int scoreBefore = count("score_events");
        String subject = sub();
        var phone = student("google", subject, null, null, "Leitora");

        var saved = post(phone, ITEMS, "{\"token\":\"" + tokenA + "\"}");
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(201);
        assertThat(field(saved.body(), "status")).isEqualTo("SAVED");
        String itemId = field(saved.body(), "itemId");
        var replay = post(phone, ITEMS, "{\"token\":\"" + tokenA + "\"}");
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(field(replay.body(), "status")).isEqualTo("ALREADY_SAVED");
        assertThat(field(replay.body(), "itemId")).isEqualTo(itemId);
        assertThat(field(post(phone, ITEMS, "{\"token\":\"" + tokenB + "\"}").body(), "status")).isEqualTo("SAVED");

        var laptop = student("google", subject, null, null, null);
        var list = get(laptop, ITEMS);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(list.body()).contains("Material A").contains("Material B").doesNotContain("texto A");
        var content = get(laptop, ITEMS + "/" + itemId);
        assertThat(content.statusCode()).isEqualTo(200);
        assertThat(content.body()).contains("texto A");

        assertThat(count("students")).isEqualTo(studentsBefore);
        assertThat(count("enrollments")).isEqualTo(enrollmentsBefore);
        assertThat(count("score_events")).as("no XP for Library").isEqualTo(scoreBefore);
        assertThat(get(laptop, "/api/student-account/session").body()).contains("\"studentId\":null");
        assertThat(get(teacher, "/api/library/resources/" + first + "/shares").body())
                .contains("\"saveCount\":1").doesNotContain("Leitora").doesNotContain(subject);
    }

    @Test
    void concurrentSavesOfTheSameResourceKeepOneItem() throws Exception {
        UUID resource = libraryResource("Corrida", "texto", LearningResourceFormat.MARKDOWN, null);
        String token = share(teacher(), resource, "SAVE");
        var student = student("google", sub(), null, null, "Corrida");
        var pool = Executors.newFixedThreadPool(5);
        var gate = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(pool.submit(() -> { gate.await(); return post(student, ITEMS, "{\"token\":\"" + token + "\"}"); }));
        }
        gate.countDown();
        int created = 0;
        for (var future : futures) {
            var response = future.get();
            assertThat(response.statusCode()).as(response.body()).isIn(200, 201);
            if (response.statusCode() == 201) created++;
        }
        pool.shutdown();
        assertThat(created).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from student_library_items where source_resource_id = ?",
                Integer.class, resource)).isEqualTo(1);
    }

    @Test
    void newerVersionReplacesTheSavedItemOnlyOnExplicitRequest() throws Exception {
        var teacher = teacher();
        UUID resource = libraryResource("Versões", "versão 1", LearningResourceFormat.MARKDOWN, null);
        String v1 = share(teacher, resource, "SAVE");
        var student = student("google", sub(), null, null, "Versões");
        String itemId = field(post(student, ITEMS, "{\"token\":\"" + v1 + "\"}").body(), "itemId");
        resources.updateLibrary(resource, input("Versões", "versão 2", LearningResourceFormat.MARKDOWN, null));
        assertThat(get(student, ITEMS + "/" + itemId).body()).contains("versão 1").doesNotContain("versão 2");

        String v2 = share(teacher, resource, "SAVE");
        assertThat(post(student, PREVIEW, "{\"token\":\"" + v2 + "\"}").body())
                .contains("\"alreadySaved\":true").contains("\"savedVersionDiffers\":true");
        var kept = post(student, ITEMS, "{\"token\":\"" + v2 + "\"}");
        assertThat(field(kept.body(), "status")).isEqualTo("ALREADY_SAVED");
        assertThat(kept.body()).contains("\"newerVersionAvailable\":true");
        assertThat(get(student, ITEMS + "/" + itemId).body()).contains("versão 1");
        var updated = post(student, ITEMS, "{\"token\":\"" + v2 + "\",\"replaceVersion\":true}");
        assertThat(field(updated.body(), "status")).isEqualTo("UPDATED");
        assertThat(get(student, ITEMS + "/" + itemId).body()).contains("versão 2");
    }

    @Test
    void revocationAndExpiryStopNewUseButKeepItemsAlreadySaved() throws Exception {
        var teacher = teacher();
        UUID resource = libraryResource("Revogável", "texto revogável", LearningResourceFormat.MARKDOWN, null);
        var issued = post(teacher, "/api/library/resources/" + resource + "/shares", "{\"permission\":\"SAVE\",\"validDays\":7}");
        String token = field(issued.body(), "token");
        String shareId = field(issued.body(), "id");
        var student = student("google", sub(), null, null, "Revogada");
        String itemId = field(post(student, ITEMS, "{\"token\":\"" + token + "\"}").body(), "itemId");

        assertThat(post(student, "/api/library/shares/" + shareId + "/revoke", "{}").statusCode()).isEqualTo(403);
        assertThat(post(teacher, "/api/library/shares/" + shareId + "/revoke", "{}").statusCode()).isEqualTo(204);
        assertThat(post(teacher, "/api/library/shares/" + shareId + "/revoke", "{}").statusCode()).as("idempotent").isEqualTo(204);
        var revoked = post(student, PREVIEW, "{\"token\":\"" + token + "\"}");
        assertThat(revoked.statusCode()).isEqualTo(404);
        var other = student("google", sub(), null, null, "Outra");
        assertThat(post(other, ITEMS, "{\"token\":\"" + token + "\"}").statusCode()).isEqualTo(404);
        assertThat(get(student, ITEMS + "/" + itemId).statusCode()).as("kept after revocation").isEqualTo(200);
        assertThat(get(teacher, "/api/library/resources/" + resource + "/shares").body()).contains("\"status\":\"REVOKED\"");

        String expiring = share(teacher, resource, "VIEW");
        jdbc.update("update library_share_links set created_at = now() - interval '2 days', expires_at = now() - interval '1 second'"
                + " where revoked_at is null and source_resource_id = ?", resource);
        var expired = send(new Browser(client(), null).client(), "POST", PREVIEW, "{\"token\":\"" + expiring + "\"}", null);
        assertThat(expired.statusCode()).isEqualTo(404);
        var unknown = send(new Browser(client(), null).client(), "POST", PREVIEW,
                "{\"token\":\"ABCDEFGHIJKLMNOPQRSTUVWXYZ234567\"}", null);
        assertThat(field(expired.body(), "message")).isEqualTo(field(revoked.body(), "message"))
                .isEqualTo(field(unknown.body(), "message"));
        assertThat(send(new Browser(client(), null).client(), "POST", PREVIEW, "{\"token\":\"curto\"}", null)
                .statusCode()).isEqualTo(400);
    }

    @Test
    void onlyTheOwnerReadsOrRemovesAnItemAndRemovalKeepsTheTeacherResource() throws Exception {
        UUID resource = libraryResource("Privado", "texto privado", LearningResourceFormat.MARKDOWN, null);
        String token = share(teacher(), resource, "SAVE");
        var owner = student("google", sub(), null, null, "Dona");
        var intruder = student("microsoft", sub(), null, null, "Intrusa");
        String itemId = field(post(owner, ITEMS, "{\"token\":\"" + token + "\"}").body(), "itemId");

        assertThat(get(intruder, ITEMS).body()).isEqualTo("[]");
        assertThat(get(intruder, ITEMS + "/" + itemId).statusCode()).isEqualTo(404);
        assertThat(send(intruder.client(), "DELETE", ITEMS + "/" + itemId, null, intruder.csrf()).statusCode()).isEqualTo(404);
        assertThat(send(owner.client(), "DELETE", ITEMS + "/" + itemId, null, null).statusCode()).as("CSRF").isEqualTo(403);
        var teacherBrowser = teacher();
        assertThat(get(teacherBrowser, ITEMS).statusCode()).isEqualTo(403);

        assertThat(send(owner.client(), "DELETE", ITEMS + "/" + itemId, null, owner.csrf()).statusCode()).isEqualTo(204);
        assertThat(get(owner, ITEMS + "/" + itemId).statusCode()).isEqualTo(404);
        assertThat(send(owner.client(), "DELETE", ITEMS + "/" + itemId, null, owner.csrf()).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from learning_resources where id = ?", Integer.class, resource)).isEqualTo(1);
        assertThat(field(post(owner, ITEMS, "{\"token\":\"" + token + "\"}").body(), "status")).isEqualTo("SAVED");
    }

    @Test
    void hardDeletingTheClassroomKeepsPersonalItemsAndClosesItsLinks() throws Exception {
        var teacher = teacher();
        String name = "Turma apagada " + UUID.randomUUID().toString().substring(0, 6);
        UUID room = classrooms.create(name, null).id();
        UUID resource = resources.create(new LearningResourceService.ResourceInput(room, "Material da turma",
                null, "conteúdo da turma", LearningResourceFormat.MARKDOWN, LearningResourceSourceType.MANUAL,
                null, null, null, null, null)).id();
        String token = share(teacher, resource, "SAVE");
        var student = student("google", sub(), null, null, "Guardiã");
        String itemId = field(post(student, ITEMS, "{\"token\":\"" + token + "\"}").body(), "itemId");

        classrooms.hardDelete(room, name);
        assertThat(jdbc.queryForObject("select count(*) from learning_resources where id = ?", Integer.class, resource)).isZero();
        var item = get(student, ITEMS + "/" + itemId);
        assertThat(item.statusCode()).isEqualTo(200);
        assertThat(item.body()).contains("conteúdo da turma");
        assertThat(jdbc.queryForObject("select count(*) from learning_resource_snapshots where source_resource_id = ?"
                + " and resource_id is null", Integer.class, resource)).isEqualTo(1);
        assertThat(post(student, PREVIEW, "{\"token\":\"" + token + "\"}").statusCode()).isEqualTo(404);
    }

    @Test
    void anonymizingTheLinkedStudentErasesTheAccountCollection() throws Exception {
        UUID resource = libraryResource("LGPD", "texto", LearningResourceFormat.MARKDOWN, null);
        String token = share(teacher(), resource, "SAVE");
        var browser = student("google", sub(), null, null, "Titular LGPD");
        UUID accountId = UUID.fromString(field(get(browser, "/api/student-account/session").body(), "accountId"));
        var student = students.create(null, "Titular LGPD", null, null);
        jdbc.update("update student_accounts set student_id = ? where id = ?", student.id(), accountId);
        post(browser, ITEMS, "{\"token\":\"" + token + "\"}");
        assertThat(jdbc.queryForObject("select count(*) from student_library_items where account_id = ?",
                Integer.class, accountId)).isEqualTo(1);

        anonymization.anonymize(student.id(), "Titular LGPD");
        assertThat(jdbc.queryForObject("select count(*) from student_library_items where account_id = ?",
                Integer.class, accountId)).isZero();
        assertThat(get(browser, ITEMS).statusCode()).isEqualTo(401);
    }

    // ------------------------------------------------------------- helpers

    private UUID libraryResource(String title, String body, LearningResourceFormat format, String url) {
        return resources.createLibrary(input(title, body, format, url)).id();
    }

    private static LearningResourceService.LibraryResourceInput input(String title, String body,
                                                                      LearningResourceFormat format, String url) {
        return new LearningResourceService.LibraryResourceInput(title, null, body, format,
                url == null ? LearningResourceSourceType.MANUAL : LearningResourceSourceType.URL, url, null,
                null, List.of(), null);
    }

    private String share(Browser teacher, UUID resource, String permission) throws Exception {
        var issued = post(teacher, "/api/library/resources/" + resource + "/shares",
                "{\"permission\":\"" + permission + "\",\"validDays\":7}");
        assertThat(issued.statusCode()).as(issued.body()).isEqualTo(201);
        return field(issued.body(), "token");
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private static String sub() { return "sub-" + UUID.randomUUID(); }
}
