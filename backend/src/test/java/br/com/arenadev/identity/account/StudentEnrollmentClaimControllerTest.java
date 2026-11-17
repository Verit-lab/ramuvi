package br.com.arenadev.identity.account;

import br.com.arenadev.auth.LoginRateLimitService;
import br.com.arenadev.auth.LoginRateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.time.Duration;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StudentEnrollmentClaimControllerTest {
    @Test
    void redemptionDerivesIssuerAndSubjectOnlyFromTheAuthenticatedIdToken() {
        var accounts = mock(StudentAccountService.class);
        var controller = new StudentEnrollmentClaimController(accounts, limiter());
        String token = "a".repeat(43);
        String issuer = "https://accounts.google.com";
        String subject = UUID.randomUUID().toString();
        UUID studentId = UUID.randomUUID();
        var result = new StudentAccountService.ClaimedEnrollment(
                studentId, UUID.randomUUID(), UUID.randomUUID(), "Turma exemplo");
        when(accounts.claimEnrollmentInviteForVerifiedOidc(token, issuer, subject)).thenReturn(result);

        var principal = principal(issuer, subject);
        var response = controller.claim(principal,
                new StudentEnrollmentClaimController.ClaimRequest(token), request());
        assertThat(response.getBody()).isEqualTo(result);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        verify(accounts).claimEnrollmentInviteForVerifiedOidc(token, issuer, subject);
        verifyNoMoreInteractions(accounts);
    }

    @Test
    void membershipListUsesOnlyTheCurrentVerifiedPrincipal() {
        var accounts = mock(StudentAccountService.class);
        var controller = new StudentEnrollmentClaimController(accounts, limiter());
        var principal = principal("https://accounts.google.com", "my-sub");
        var member = new StudentAccountService.ActiveClassroom(
                UUID.randomUUID(), UUID.randomUUID(), "Turma", true);
        when(accounts.listActiveClassroomsForVerifiedOidc(
                "https://accounts.google.com", "my-sub")).thenReturn(List.of(member));
        var response = controller.classrooms(principal);
        assertThat(response.getBody()).containsExactly(member);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void absentPrincipalCannotConsumeAValidCode() {
        var service = mock(StudentAccountService.class);
        var controller = new StudentEnrollmentClaimController(service, limiter());
        assertThatThrownBy(() -> controller.claim(
                null, new StudentEnrollmentClaimController.ClaimRequest("a".repeat(43)), request()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(service);
    }

    @Test
    void rateLimiterRejectsRepeatedInvalidInvitesForSameAuthenticatedClient() {
        var accounts = mock(StudentAccountService.class);
        var limiter = new LoginRateLimitService(2, Duration.ofMinutes(1),
                Duration.ofMinutes(5), Clock.systemUTC());
        var controller = new StudentEnrollmentClaimController(accounts, limiter);
        String token = "a".repeat(43);
        var principal = principal("https://accounts.google.com", "subject");
        when(accounts.claimEnrollmentInviteForVerifiedOidc(token,
                "https://accounts.google.com", "subject"))
                .thenThrow(new IllegalArgumentException("invalid invite"));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> controller.claim(principal,
                    new StudentEnrollmentClaimController.ClaimRequest(token), request()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> controller.claim(principal,
                new StudentEnrollmentClaimController.ClaimRequest(token), request()))
                .isInstanceOf(LoginRateLimitExceededException.class);
        verify(accounts, times(2)).claimEnrollmentInviteForVerifiedOidc(
                token, "https://accounts.google.com", "subject");
    }

    private static LoginRateLimitService limiter() {
        return new LoginRateLimitService(10, Duration.ofMinutes(1),
                Duration.ofMinutes(1), Clock.systemUTC());
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        return request;
    }

    private static DefaultOidcUser principal(String issuer, String subject) {
        Instant now = Instant.now();
        var token = new OidcIdToken("test-only", now, now.plusSeconds(60),
                Map.of("iss", issuer, "sub", subject));
        // This is a server-side CONTROLLER UNIT TEST, not external JWT validation.
        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")), token);
    }
}
