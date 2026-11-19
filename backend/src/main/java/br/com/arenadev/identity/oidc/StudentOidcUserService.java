package br.com.arenadev.identity.oidc;

import br.com.arenadev.identity.account.StudentAccountService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Receives only a verified OIDC ID Token, after Spring has checked its JWT
 * signature, issuer, audience, nonce and Authorization Code state.
 * No teacher role from claims; optional email linking only with a verified
 * provider address matched to an unclaimed, teacher-entered student record.
 */
@Service
@ConditionalOnProperty(prefix = "app.student-identity.oidc", name = "enabled", havingValue = "true")
public class StudentOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OidcUserService delegate = new OidcUserService();
    private final StudentAccountService accounts;
    @Value("${app.student-identity.auto-email-link-enabled:false}")
    private boolean autoEmailLinkEnabled;

    public StudentOidcUserService(StudentAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser user = delegate.loadUser(request);
        String issuer = user.getIdToken().getClaimAsString(IdTokenClaimNames.ISS);
        String subject = user.getIdToken().getClaimAsString(IdTokenClaimNames.SUB);
        String expected = request.getClientRegistration().getProviderDetails().getIssuerUri();
        if (issuer == null || subject == null || subject.isBlank() || !issuer.equals(expected)) {
            throw new BadCredentialsException("Provedor OIDC não corresponde à origem confiável.");
        }
        accounts.provisionVerifiedIdentity(new StudentAccountService.VerifiedIdentity(
                StudentAccountService.Method.OIDC, issuer, subject));
        if (autoEmailLinkEnabled && Boolean.TRUE.equals(user.getEmailVerified())) {
            accounts.linkExistingStudentByVerifiedEmail(issuer, subject, user.getEmail());
        }
        // A student can NEVER obtain ROLE_TEACHER through any external claim.
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")), user.getIdToken());
    }
}
