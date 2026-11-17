package br.com.arenadev.classroom;

import br.com.arenadev.shared.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Removes a student's personal data while keeping the class history coherent:
 * XP ledger, participation and aggregated evidence keep pointing at the same
 * row, now shown under a pseudonym. Device claims, live tokens and external
 * (Teams) links are dropped. Free-text answers written by the student are
 * learning evidence and stay; the teacher reviews them if needed.
 */
@Service
public class StudentAnonymizationService {
    private final StudentRepository students;
    private final JdbcTemplate jdbc;

    public StudentAnonymizationService(StudentRepository students, JdbcTemplate jdbc) {
        this.students = students;
        this.jdbc = jdbc;
    }

    @Transactional
    public StudentService.StudentView anonymize(UUID studentId, String confirmation) {
        Student student = students.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Aluno não encontrado."));
        if (student.isAnonymized()) return StudentService.StudentView.from(student);
        if (confirmation == null || !confirmation.trim().equalsIgnoreCase(student.getName().trim())) {
            throw new IllegalArgumentException("Digite o nome do aluno exatamente como aparece para confirmar.");
        }

        student.anonymize(pseudonym(studentId), Instant.now());
        students.flush();

        // Verified accounts and outstanding invitations must lose access before PII is cleared.
        // History and XP remain on Student/Enrollment rows, never on login credentials.
        jdbc.update("""
                delete from student_enrollment_invites
                where enrollment_id in (select id from enrollments where student_id = ?)
                """, studentId);
        // Erase OIDC subjects and email-OTP addresses rather than retaining PII in a revoked credential.
        jdbc.update("""
                delete from student_account_identities
                where account_id in (select id from student_accounts where student_id = ?)
                """, studentId);
        jdbc.update("""
                update student_accounts set student_id = null, status = 'DISABLED',
                    disabled_at = now() where student_id = ?
                """, studentId);

        jdbc.update("update enrollments set preferred_name = null where student_id = ?", studentId);
        jdbc.update("update external_result_rows set participant_name = null where matched_student_id = ?", studentId);
        jdbc.update("""
                delete from enrollment_device_claims
                where enrollment_id in (select id from enrollments where student_id = ?)
                """, studentId);
        jdbc.update("""
                delete from external_student_links
                where enrollment_id in (select id from enrollments where student_id = ?)
                """, studentId);
        jdbc.update("""
                update session_participants set access_token_hash = null, connected = false
                where student_id = ?
                """, studentId);
        return StudentService.StudentView.from(student);
    }

    static String pseudonym(UUID studentId) {
        return "Aluno removido " + studentId.toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }
}
