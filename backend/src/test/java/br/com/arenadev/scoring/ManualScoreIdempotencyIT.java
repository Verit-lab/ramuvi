package br.com.arenadev.scoring;

import br.com.arenadev.support.SharedPostgres;
import br.com.arenadev.classroom.ClassroomService;
import br.com.arenadev.classroom.StudentService;
import br.com.arenadev.session.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class ManualScoreIdempotencyIT {
    @DynamicPropertySource static void db(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, ManualScoreIdempotencyIT.class);
    }
    @Autowired ClassroomService classrooms;
    @Autowired StudentService students;
    @Autowired SessionService sessions;
    @Autowired ScoreEventService scores;
    @Autowired ScoreEventRepository events;

    private Fixture fixture() {
        String marker = UUID.randomUUID().toString().substring(0, 7).toUpperCase();
        var classroom = classrooms.create("Idempotencia " + marker, "ID" + marker);
        var student = students.create("ID-" + marker, "Aluno Idempotente", null);
        classrooms.enroll(classroom.id(), student.id());
        var session = sessions.start(classroom.id(), "XP em rede incerta", Set.of(student.id()));
        return new Fixture(classroom.id(), student.id(), session.id());
    }
    private record Fixture(UUID classroomId, UUID studentId, UUID sessionId) {
        ScoreEventService.CreateScoreEvent request(UUID id, int points) {
            return new ScoreEventService.CreateScoreEvent(classroomId, studentId, sessionId,
                    points, ScoreCategory.BONUS, "Participacao", ScoreSource.ARENA, null, null, id);
        }
    }

    @Test
    void retryAfterLostResponseReturnsTheSamePersistedEventWithoutDoublingXp() {
        var fx = fixture();
        var key = UUID.randomUUID();
        var first = scores.create(fx.request(key, 10));
        var repeated = scores.create(fx.request(key, 10));
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(events.findBySessionIdOrderByCreatedAtAsc(fx.sessionId())).hasSize(1);
        assertThat(events.findByCreateRequestId(key)).isPresent();
    }

    @Test
    void sameKeyWithDifferentPayloadReturnsConflictAndDoesNotCreateXp() {
        var fx = fixture();
        var key = UUID.randomUUID();
        scores.create(fx.request(key, 5));
        assertThatThrownBy(() -> scores.create(fx.request(key, 20)))
                .isInstanceOf(ScoreConflictException.class)
                .hasMessageContaining("diferentes");
        assertThat(events.findBySessionIdOrderByCreatedAtAsc(fx.sessionId())).hasSize(1);
    }

    @Test
    void concurrentEqualRequestsSerializeOnPostgresAndReturnOneEvent() throws Exception {
        var fx = fixture();
        var key = UUID.randomUUID();
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { gate.await(); return scores.create(fx.request(key, 7)); });
            var b = executor.submit(() -> { gate.await(); return scores.create(fx.request(key, 7)); });
            gate.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS).id()).isEqualTo(b.get(20, TimeUnit.SECONDS).id());
        }
        assertThat(events.findBySessionIdOrderByCreatedAtAsc(fx.sessionId())).hasSize(1);
    }

    @Test
    void retriedBatchWithIndividualKeysDoesNotDoubleXpOrRegenerateEventIds() {
        var fx = fixture();
        var keyA = UUID.randomUUID();
        var keyB = UUID.randomUUID();
        var batch = java.util.List.of(fx.request(keyA, 5), fx.request(keyB, 7));
        var first = scores.createBatch(batch);
        var replay = scores.createBatch(batch);

        assertThat(replay).extracting(ScoreEventService.ScoreEventView::id)
                .containsExactly(first.get(0).id(), first.get(1).id());
        assertThat(events.findBySessionIdOrderByCreatedAtAsc(fx.sessionId())).hasSize(2);
    }

    @Test
    void batchWithCommittedAndNewKeysReusesOldEventAndInsertsOnlyNewOne() {
        var fx = fixture();
        var priorKey = UUID.randomUUID();
        var newKey = UUID.randomUUID();
        var prior = scores.create(fx.request(priorKey, 5));

        var response = scores.createBatch(java.util.List.of(
                fx.request(priorKey, 5), fx.request(newKey, 8)));
        assertThat(response.getFirst().id()).isEqualTo(prior.id());
        assertThat(response.getLast().id()).isNotEqualTo(prior.id());
        assertThat(events.findBySessionIdOrderByCreatedAtAsc(fx.sessionId())).hasSize(2);
    }

    @Test
    void duplicateKeyWithinBatchIsOnlyInsertedOnce() {
        var fx = fixture();
        var key = UUID.randomUUID();
        var request = fx.request(key, 4);
        var response = scores.createBatch(java.util.List.of(request, request));

        assertThat(response).hasSize(2);
        assertThat(response.get(0).id()).isEqualTo(response.get(1).id());
        assertThat(events.findBySessionIdOrderByCreatedAtAsc(fx.sessionId())).hasSize(1);
    }

    @Test
    void batchWithReusedKeyAndDifferentPayloadRollsBackAllNewEvents() {
        var fx = fixture();
        var key = UUID.randomUUID();
        scores.create(fx.request(key, 5));
        var newKey = UUID.randomUUID();

        assertThatThrownBy(() -> scores.createBatch(java.util.List.of(
                fx.request(newKey, 3), fx.request(key, 8))))
                .isInstanceOf(ScoreConflictException.class);
        assertThat(events.findBySessionIdOrderByCreatedAtAsc(fx.sessionId())).hasSize(1);
        assertThat(events.findByCreateRequestId(newKey)).isEmpty();
    }

    @Test
    void reversedBatchesWithSameRequestKeysSerializeWithoutDeadlock() throws Exception {
        var fx = fixture();
        var a = fx.request(UUID.randomUUID(), 5);
        var b = fx.request(UUID.randomUUID(), 7);
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                gate.await(); return scores.createBatch(java.util.List.of(a, b));
            });
            var reverse = executor.submit(() -> {
                gate.await(); return scores.createBatch(java.util.List.of(b, a));
            });
            gate.countDown();
            var resultA = first.get(30, TimeUnit.SECONDS);
            var resultB = reverse.get(30, TimeUnit.SECONDS);
            assertThat(resultA).extracting(ScoreEventService.ScoreEventView::id)
                    .containsExactly(resultB.get(1).id(), resultB.get(0).id());
        }
        assertThat(events.findBySessionIdOrderByCreatedAtAsc(fx.sessionId())).hasSize(2);
    }

    @Test
    void noKeyBatchStillPermitsRepeatedLegacyPoints() {
        var fx = fixture();
        var request = fx.request(null, 4);
        var first = scores.createBatch(java.util.List.of(request));
        var second = scores.createBatch(java.util.List.of(request));
        assertThat(first.get(0).id()).isNotEqualTo(second.get(0).id());
        assertThat(events.findBySessionIdOrderByCreatedAtAsc(fx.sessionId())).hasSize(2);
    }

    @Test
    void legacyClientsWithoutKeysRemainCompatibleButAreNotIdempotent() {
        var fx = fixture();
        var first = scores.create(fx.request(null, 3));
        var second = scores.create(fx.request(null, 3));
        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(events.findBySessionIdOrderByCreatedAtAsc(fx.sessionId())).hasSize(2);
    }
}
