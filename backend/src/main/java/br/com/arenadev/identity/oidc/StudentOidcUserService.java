package br.com.arenadev.identity.oidc;

import br.com.arenadev.identity.account.StudentAccountService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * No teacher role from claims. Login NEVER links a student record by e-mail:
 * without proof of a classroom (join link or individual invite) a matching
 * address is not enough (ADR-0074). Linking happens in ClassroomJoinLinkService.
 */
@Service
@ConditionalOnProperty(prefix = "app.student-identity.oidc", name = "enabled", havingValue = "true")
public class StudentOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OidcUserService delegate = new OidcUserService();
    private final StudentAccountService accounts;

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
        // A student can NEVER obtain ROLE_TEACHER through any external claim.
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")), user.getIdToken());
    }
}
