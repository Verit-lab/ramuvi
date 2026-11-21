package br.com.arenadev.study;

import br.com.arenadev.classroom.ClassroomService;
import br.com.arenadev.classroom.EnrollmentRepository;
import br.com.arenadev.classroom.StudentService;
import br.com.arenadev.identity.DeviceClaimScope;
import br.com.arenadev.identity.DeviceClaimService;
import br.com.arenadev.identity.EnrollmentDeviceClaim;
import br.com.arenadev.identity.EnrollmentDeviceClaimRepository;
import br.com.arenadev.journey.JourneyCompletionRule;
import br.com.arenadev.journey.JourneyRedemptionService;
import br.com.arenadev.journey.JourneyService;
import br.com.arenadev.journey.JourneyUnlockRule;
import br.com.arenadev.session.SessionJoinService;
import br.com.arenadev.session.SessionService;
import br.com.arenadev.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * v0.12.1 P0 (ADR-0075): a shared Journey code or a live-session join plus a typed
 * name/registration/email must never yield a study credential (X-Study-Token).
 * Default flags only: legacy claims accepted, live claims study-disabled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.join-rate-limit.max-failures=5",
        "app.security.join-rate-limit.block-seconds=60"
})
class LegacyStudyCredentialHardeningIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, LegacyStudyCredentialHardeningIT.class);
    }

    @LocalServerPort private int port;
    @Autowired private ClassroomService classrooms;
    @Autowired private StudentService students;
    @Autowired private SessionService sessions;
    @Autowired private SessionJoinService joins;
    @Autowired private JourneyService journeys;
    @Autowired private JourneyRedemptionService redemptions;
    @Autowired private EnrollmentDeviceClaimRepository claims;
    @Autowired private EnrollmentRepository enrollments;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void bootstrapWithSharedJourneyCodeAndVictimIdentityIsGoneAndIssuesNothing() throws Exception {
        Victim victim = victimWithPublishedJourney();
        long claimsBefore = claims.count();
        for (String declared : List.of(victim.name(), victim.registration(), victim.email(), "ninguém")) {
            var response = post("/api/learn/journeys/redeem/bootstrap",
                    "{\"code\":\"" + victim.journeyCode() + "\",\"identity\":\"" + declared + "\"}", null);
            assertThat(response.statusCode()).isEqualTo(410);
            assertThat(response.body())
                    .contains("/learn")
                    .doesNotContain("deviceToken")
                    .doesNotContain(victim.registration())
                    .doesNotContain(victim.email());
            assertThat(response.headers().firstValue("Cache-Control")).hasValueSatisfying(
                    value -> assertThat(value).contains("no-store"));
        }
        assertThat(claims.count()).isEqualTo(claimsBefore);
        assertThat(jdbc.queryForObject(
                "select count(*) from student_journey_access where student_id = ?",
                Integer.class, victim.studentId())).isZero();
    }

    @Test
    void liveRememberedDeviceIsLiveOnlyAndCannotReachStudyEndpoints() throws Exception {
        Victim victim = victimWithPublishedJourney();
        var session = sessions.start(victim.classroomId(), "Aula P0", Set.of(victim.studentId()));
        String joinCode = joins.ensureCode(session.id()).code();
        // Typed full name in a live session (ADR-0005) is declared information.
        var joined = joins.join(joinCode, victim.name(), true);
        assertThat(joined.deviceToken()).isNotBlank();
        EnrollmentDeviceClaim stored = claims.findByTokenHash(sha256Hex(joined.deviceToken())).orElseThrow();
        assertThat(stored.getScope()).isEqualTo(DeviceClaimScope.LIVE);

        for (String path : List.of("/api/learn/journeys", "/api/learn/profile", "/api/learn/achievements",
                "/api/learn/journeys/" + victim.journeyId())) {
            assertThat(get(path, joined.deviceToken()).statusCode()).as(path).isEqualTo(401);
        }
        assertThat(put("/api/learn/profile", "{\"name\":\"Invasor\",\"email\":\"x@evil.example\"}",
                joined.deviceToken()).statusCode()).isEqualTo(401);
        assertThat(post("/api/learn/journeys/redeem", "{\"code\":\"" + victim.journeyCode() + "\"}",
                joined.deviceToken()).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("select email from students where id = ?", String.class,
                victim.studentId())).isEqualTo(victim.email());

        // The live purpose of "remember this device" keeps working.
        assertThat(get("/api/learn/live", joined.deviceToken()).statusCode()).isEqualTo(200);
        assertThat(joins.recognizeDevice(joinCode, joined.deviceToken())).isNotNull();
    }

    @Test
    void legacyClaimStaysUsableUntilExpiryButIsNeverRenewedAndHasAKillSwitch() throws Exception {
        Victim victim = victimWithPublishedJourney();
        String token = "legacy-" + UUID.randomUUID();
        Instant expiry = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MILLIS);
        jdbc.update("""
                insert into enrollment_device_claims
                    (id, enrollment_id, token_hash, created_at, last_used_at, expires_at, scope)
                values (?, ?, ?, now(), now(), ?, 'LEGACY')
                """, UUID.randomUUID(), victim.enrollmentId(), sha256Hex(token), Timestamp.from(expiry));

        assertThat(get("/api/learn/journeys", token).statusCode()).isEqualTo(200);
        Timestamp after = jdbc.queryForObject(
                "select expires_at from enrollment_device_claims where token_hash = ?",
                Timestamp.class, sha256Hex(token));
        assertThat(after.toInstant()).isEqualTo(expiry);

        // Same repository, kill switch OFF: legacy claims no longer authorize study.
        DeviceClaimService killed = new DeviceClaimService(claims, false, false);
        tx.executeWithoutResult(status ->
                assertThatThrownBy(() -> killed.requireUsableForStudy(token))
                        .isInstanceOf(IllegalArgumentException.class));
        tx.executeWithoutResult(status ->
                assertThat(killed.requireUsable(token).getScope()).isEqualTo(DeviceClaimScope.LEGACY));
    }

    @Test
    void operatorOptInForLiveStudyIsShortLivedAndNeverSlidesPastThirtyDays() throws Exception {
        Victim victim = victimWithPublishedJourney();
        DeviceClaimService optIn = new DeviceClaimService(claims, true, true);
        var issued = tx.execute(status -> optIn.issueForLiveSession(
                enrollments.findById(victim.enrollmentId()).orElseThrow()));
        assertThat(issued.expiresAt()).isBefore(Instant.now().plus(Duration.ofDays(31)));
        tx.executeWithoutResult(status -> {
            var claim = optIn.requireUsableForStudy(issued.token());
            assertThat(claim.getScope()).isEqualTo(DeviceClaimScope.LIVE_STUDY);
            optIn.markUsed(claim);
        });
        Timestamp after = jdbc.queryForObject(
                "select expires_at from enrollment_device_claims where token_hash = ?",
                Timestamp.class, sha256Hex(issued.token()));
        assertThat(after.toInstant()).isBeforeOrEqualTo(issued.expiresAt().plusMillis(1)); // PG stores µs
        // When the operator turns the opt-in OFF again, those claims stop authorizing study.
        DeviceClaimService off = new DeviceClaimService(claims, false, true);
        tx.executeWithoutResult(status ->
                assertThatThrownBy(() -> off.requireUsableForStudy(issued.token()))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void journeyCodeGuessingIsThrottledPerClient() throws Exception {
        int notFound = 0;
        int throttled = 0;
        for (int attempt = 0; attempt < 8; attempt++) {
            int status = get("/api/learn/journeys/redeem/J-ZZZZ" + (char) ('A' + attempt) + "Z", null).statusCode();
            if (status == 404) notFound++;
            if (status == 429) throttled++;
        }
        assertThat(notFound).isEqualTo(5);
        assertThat(throttled).isEqualTo(3);
    }

    private Victim victimWithPublishedJourney() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var classroom = classrooms.create("Turma P0 " + suffix, "P0" + suffix);
        classrooms.update(classroom.id(), classroom.name(), classroom.code(), true,
                classroom.themeColor(), classroom.themeIcon(), true);
        String email = "vitima." + suffix.toLowerCase() + "@escola.example";
        var student = students.create("P0-" + suffix, "Vítima " + suffix, null, email);
        var enrollment = classrooms.enroll(classroom.id(), student.id());
        var journey = journeys.create(new JourneyService.JourneyInput(classroom.id(), "Jornada P0", null,
                List.of(new JourneyService.StageInput(null, "Etapa", null, null, JourneyUnlockRule.IMMEDIATE,
                        JourneyCompletionRule.MANUAL, null, null, null, null))));
        journeys.publish(journey.id());
        String code = redemptions.enable(journey.id()).code();
        return new Victim(classroom.id(), student.id(), enrollment.enrollmentId(), journey.id(), code,
                "Vítima " + suffix, "P0-" + suffix, email);
    }

    private record Victim(UUID classroomId, UUID studentId, UUID enrollmentId, UUID journeyId,
                          String journeyCode, String name, String registration, String email) { }

    private HttpResponse<String> get(String path, String studyToken) throws Exception {
        return send("GET", path, null, studyToken);
    }

    private HttpResponse<String> post(String path, String body, String studyToken) throws Exception {
        return send("POST", path, body, studyToken);
    }

    private HttpResponse<String> put(String path, String body, String studyToken) throws Exception {
        return send("PUT", path, body, studyToken);
    }

    private HttpResponse<String> send(String method, String path, String body, String studyToken) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (studyToken != null) request.header("X-Study-Token", studyToken);
        return http.send(request.method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String sha256Hex(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
