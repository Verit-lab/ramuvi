package br.com.arenadev.identity.account;

import br.com.arenadev.study.JourneyStudyService;
import br.com.arenadev.study.StudyPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * I3: resolve the authenticated issuer + subject to the canonical Student on
 * every request. Never trust account/student IDs from the browser and never
 * create duplicate progress belonging to StudentAccount.
 */
@Service
public class VerifiedStudentStudyService {
    private final StudentAccountService accounts;
    private final JourneyStudyService study;

    public VerifiedStudentStudyService(StudentAccountService accounts, JourneyStudyService study) {
        this.accounts = accounts;
        this.study = study;
    }

    @Transactional(readOnly = true)
    public List<JourneyStudyService.JourneyStudySummary> list(String issuer, String subject) {
        UUID studentId = studentId(issuer, subject);
        return studentId == null ? List.of() : study.list(StudyPrincipal.verifiedAccount(studentId));
    }

    @Transactional(readOnly = true)
    public JourneyStudyService.JourneyStudyView get(UUID journey, String issuer, String subject) {
        return study.get(journey, principal(issuer, subject));
    }

    @Transactional(readOnly = true)
    public JourneyStudyService.JourneyContentDetailView content(UUID journey, UUID content,
                                                                 String issuer, String subject) {
        return study.content(journey, content, principal(issuer, subject));
    }

    @Transactional
    public JourneyStudyService.JourneyStudyView start(UUID journey, UUID content,
                                                       String issuer, String subject) {
        return study.start(journey, content, principal(issuer, subject));
    }

    @Transactional
    public JourneyStudyService.JourneyStudyView complete(UUID journey, UUID content,
                                                          String issuer, String subject) {
        return study.complete(journey, content, principal(issuer, subject));
    }

    private StudyPrincipal principal(String issuer, String subject) {
        UUID studentId = studentId(issuer, subject);
        if (studentId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Jornada não encontrada.");
        return StudyPrincipal.verifiedAccount(studentId);
    }

    private UUID studentId(String issuer, String subject) {
        return accounts.findActiveVerifiedOidcAccount(issuer, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "A identidade verificada não está ativa."))
                .studentId();
    }
}
