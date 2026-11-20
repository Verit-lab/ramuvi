package br.com.arenadev.session;

import br.com.arenadev.classroom.Classroom;
import br.com.arenadev.classroom.ClassroomRepository;
import br.com.arenadev.classroom.Enrollment;
import br.com.arenadev.classroom.EnrollmentRepository;
import br.com.arenadev.classroom.Student;
import br.com.arenadev.identity.DisplayNamePolicy;
import br.com.arenadev.identity.DisplayNameService;
import br.com.arenadev.poll.PollService;
import br.com.arenadev.quiz.QuizService;
import br.com.arenadev.realtime.BuzzerService;
import br.com.arenadev.realtime.SessionRealtimeGateway;
import br.com.arenadev.shared.ResourceNotFoundException;
import br.com.arenadev.sessionevent.SessionEventActor;
import br.com.arenadev.sessionevent.SessionEventService;
import br.com.arenadev.sessionevent.SessionEventType;
import br.com.arenadev.timer.SessionTimerService;
import br.com.arenadev.wordcloud.WordCloudService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SessionService {
    private final ClassSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final ClassroomRepository classroomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SessionJoinService joinService;
    private final BuzzerService buzzerService;
    private final SessionTimerService timerService;
    private final SessionRealtimeGateway realtimeGateway;
    private final WordCloudService wordCloudService;
    private final PollService pollService;
    private final QuizService quizService;
    private final DisplayNameService displayNameService;
    private final SessionEventService sessionEventService;
    private final br.com.arenadev.stage.LiveStageService liveStage;

    public SessionService(
            ClassSessionRepository sessionRepository,
            SessionParticipantRepository participantRepository,
            ClassroomRepository classroomRepository,
            EnrollmentRepository enrollmentRepository,
            SessionJoinService joinService,
            BuzzerService buzzerService,
            SessionTimerService timerService,
            SessionRealtimeGateway realtimeGateway,
            WordCloudService wordCloudService,
            PollService pollService,
            QuizService quizService,
            DisplayNameService displayNameService,
            SessionEventService sessionEventService,
            br.com.arenadev.stage.LiveStageService liveStage
    ) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.classroomRepository = classroomRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.joinService = joinService;
        this.buzzerService = buzzerService;
        this.timerService = timerService;
        this.realtimeGateway = realtimeGateway;
        this.wordCloudService = wordCloudService;
        this.pollService = pollService;
        this.quizService = quizService;
        this.displayNameService = displayNameService;
        this.sessionEventService = sessionEventService;
        this.liveStage = liveStage;
    }

    @Transactional
    public SessionView start(UUID classroomId, String title, Set<UUID> presentStudentIds) {
        // Row lock on the classroom serializes concurrent starts: the second request waits,
        // then sees the first session and gets a typed 409 instead of a unique-index 400.
        Classroom classroom = lockClassroom(classroomId);
        if (!classroom.isActive()) {
            throw new IllegalArgumentException("Não é possível iniciar sessão para uma turma inativa.");
        }
        sessionRepository.findFirstByClassroomIdAndStatus(classroomId, SessionStatus.ACTIVE)
                .ifPresent(active -> {
                    throw new ActiveSessionConflictException(
                            ActiveSessionConflictException.Code.ACTIVE_SESSION_EXISTS,
                            SessionView.from(active),
                            "Já existe uma aula ativa nesta turma. Retome-a ou encerre-a antes de iniciar outra.");
                });
        return startLocked(classroom, title, presentStudentIds, Map.of());
    }

    /**
     * "Encerrar e iniciar outra" (ADR-0076 D3): two auditable transitions in one transaction,
     * guarded by the session the teacher actually saw. If another tab already changed the
     * active session, nothing is finished and a typed 409 returns the current one.
     */
    @Transactional
    public SwitchView switchActive(UUID classroomId, UUID expectedActiveSessionId, String title) {
        Classroom classroom = lockClassroom(classroomId);
        if (!classroom.isActive()) {
            throw new IllegalArgumentException("Não é possível iniciar sessão para uma turma inativa.");
        }
        ClassSession active = sessionRepository.findFirstByClassroomIdAndStatus(classroomId, SessionStatus.ACTIVE)
                .orElse(null);
        if (active == null || !active.getId().equals(expectedActiveSessionId)) {
            throw new ActiveSessionConflictException(
                    ActiveSessionConflictException.Code.ACTIVE_SESSION_CHANGED,
                    active == null ? null : SessionView.from(active),
                    "A aula ativa desta turma mudou. Atualize a página antes de continuar.");
        }
        SessionView finished = finishLocked(getEntityForUpdate(active.getId()));
        // Hibernate flushes INSERTs before UPDATEs: without this flush the new ACTIVE row
        // would hit uk_class_sessions_one_active_per_classroom before the old one is FINISHED.
        sessionRepository.flush();
        SessionView started = startLocked(classroom, title, Set.of(), Map.of("replacesSessionId", finished.id().toString()));
        return new SwitchView(finished, started);
    }

    private SessionView startLocked(Classroom classroom, String title, Set<UUID> presentStudentIds,
                                    Map<String, Object> extraEventData) {
        UUID classroomId = classroom.getId();
        List<Enrollment> enrollments = enrollmentRepository
                .findByClassroomIdAndActiveTrueOrderByStudentNameAsc(classroomId);
        Set<UUID> enrolledIds = enrollments.stream()
                .map(enrollment -> enrollment.getStudent().getId())
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> requestedPresent = presentStudentIds == null
                ? Set.of()
                : new HashSet<>(presentStudentIds);

        if (!enrolledIds.containsAll(requestedPresent)) {
            throw new IllegalArgumentException("Há alunos marcados como presentes que não pertencem à turma.");
        }

        String normalizedTitle = title == null || title.isBlank() ? "Aula" : title.trim();
        if (normalizedTitle.length() > 160) {
            throw new IllegalArgumentException("O título da aula deve ter no máximo 160 caracteres.");
        }
        ClassSession session = sessionRepository.save(new ClassSession(classroom, normalizedTitle));

        for (Enrollment enrollment : enrollments) {
            Student student = enrollment.getStudent();
            participantRepository.save(new SessionParticipant(
                    session,
                    student,
                    requestedPresent.contains(student.getId())
            ));
        }

        joinService.ensureCode(session);
        Map<String, Object> eventData = new java.util.LinkedHashMap<>();
        eventData.put("classroomName", classroom.getName());
        eventData.put("presentCount", requestedPresent.size());
        eventData.put("enrolledCount", enrollments.size());
        eventData.putAll(extraEventData);
        sessionEventService.record(
                session,
                SessionEventType.SESSION_STARTED,
                SessionEventActor.TEACHER,
                "Sessão iniciada: " + normalizedTitle,
                eventData
        );
        return SessionView.from(session);
    }

    private Classroom lockClassroom(UUID classroomId) {
        return classroomRepository.findByIdForUpdate(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Turma não encontrada."));
    }

    @Transactional(readOnly = true)
    public SessionView get(UUID id) {
        return SessionView.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public List<SessionView> listByClassroom(UUID classroomId) {
        if (!classroomRepository.existsById(classroomId)) {
            throw new ResourceNotFoundException("Turma não encontrada.");
        }
        return sessionRepository.findByClassroomIdOrderByStartedAtDesc(classroomId)
                .stream()
                .map(SessionView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ParticipantView> participants(UUID sessionId) {
        getEntity(sessionId);
        return participantRepository.findBySessionIdOrderByStudentNameAsc(sessionId)
                .stream()
                .map(this::participantView)
                .toList();
    }

    @Transactional
    public ParticipantView updatePresence(UUID sessionId, UUID participantId, boolean present) {
        ClassSession session = getEntity(sessionId);
        ensureActive(session);
        SessionParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("Participante não encontrado."));
        if (!participant.getSession().getId().equals(sessionId)) {
            throw new IllegalArgumentException("Participante não pertence à sessão informada.");
        }
        participant.setPresent(present);
        return participantView(participant);
    }


    @Transactional
    public ParticipantView releaseDevice(UUID sessionId, UUID participantId) {
        ClassSession session = getEntity(sessionId);
        ensureActive(session);
        return participantView(joinService.releaseDevice(sessionId, participantId));
    }

    @Transactional
    public SessionView finish(UUID sessionId) {
        return finishLocked(getEntityForUpdate(sessionId));
    }

    private SessionView finishLocked(ClassSession session) {
        UUID sessionId = session.getId();
        ensureActive(session);
        session.finish();
        liveStage.revokeForFinishedSession(sessionId);
        joinService.deactivate(sessionId);
        buzzerService.closeForFinishedSession(sessionId);
        timerService.cancelOpenForFinishedSession(sessionId);
        wordCloudService.closeOpenForFinishedSession(sessionId);
        pollService.closeOpenForFinishedSession(sessionId);
        quizService.closeOpenForFinishedSession(sessionId);
        sessionEventService.record(
                session,
                SessionEventType.SESSION_FINISHED,
                SessionEventActor.TEACHER,
                "Sessão encerrada: " + session.getTitle(),
                Map.of(
                        "startedAt", session.getStartedAt().toString(),
                        "endedAt", session.getEndedAt().toString()
                )
        );

        SessionView view = SessionView.from(session);
        realtimeGateway.broadcastAfterCommit(sessionId, "SESSION_FINISHED", view);
        realtimeGateway.broadcastProjectorsAfterCommit(sessionId, "SESSION_FINISHED", view);
        return view;
    }

    private ParticipantView participantView(SessionParticipant participant) {
        Student student = participant.getStudent();
        UUID classroomId = participant.getSession().getClassroom().getId();
        return new ParticipantView(
                participant.getId(),
                student.getId(),
                student.getRegistration(),
                student.getName(),
                student.getNickname(),
                displayNameService.preferredName(classroomId, student),
                displayNameService.resolve(classroomId, student, DisplayNamePolicy.PREFERRED_NAME),
                participant.isPresent(),
                participant.isConnected()
        );
    }

    private ClassSession getEntity(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada."));
    }

    private ClassSession getEntityForUpdate(UUID id) {
        return sessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada."));
    }

    private static void ensureActive(ClassSession session) {
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Sessão não está ativa.");
        }
    }

    public record SessionView(
            UUID id,
            UUID classroomId,
            String classroomName,
            String title,
            SessionStatus status,
            Instant startedAt,
            Instant endedAt
    ) {
        static SessionView from(ClassSession session) {
            return new SessionView(
                    session.getId(),
                    session.getClassroom().getId(),
                    session.getClassroom().getName(),
                    session.getTitle(),
                    session.getStatus(),
                    session.getStartedAt(),
                    session.getEndedAt()
            );
        }
    }

    public record SwitchView(SessionView finished, SessionView started) {
    }

    public record ParticipantView(
            UUID id,
            UUID studentId,
            String registration,
            String name,
            String nickname,
            String preferredName,
            String displayName,
            boolean present,
            boolean connected
    ) {
    }
}
