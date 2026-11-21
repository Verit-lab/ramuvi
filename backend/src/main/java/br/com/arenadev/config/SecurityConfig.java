package br.com.arenadev.config;

import br.com.arenadev.auth.LoginRateLimitService;
import br.com.arenadev.identity.oidc.StudentOidcUserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService teacherUserDetailsService(
            PasswordEncoder encoder,
            Environment environment,
            @Value("${app.teacher.username:professor}") String username,
            @Value("${app.teacher.password:arena-dev-change-me}") String password
    ) {
        // Fails fast: a production instance must never accept the public default.
        TeacherCredentialPolicy.validate(environment.getActiveProfiles(), password);
        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(encoder.encode(password))
                        .roles("TEACHER")
                        .build()
        );
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    LoginRateLimitService loginRateLimitService(
            @Value("${app.security.login-rate-limit.max-failures:5}") int maxFailures,
            @Value("${app.security.login-rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${app.security.login-rate-limit.block-seconds:300}") long blockSeconds
    ) {
        return new LoginRateLimitService(
                maxFailures,
                Duration.ofSeconds(windowSeconds),
                Duration.ofSeconds(blockSeconds),
                Clock.systemUTC()
        );
    }

    /** Failed student identifications per client and join code (enumeration, impersonation attempts). */
    @Bean
    LoginRateLimitService joinRateLimitService(
            @Value("${app.security.join-rate-limit.max-failures:10}") int maxFailures,
            @Value("${app.security.join-rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${app.security.join-rate-limit.block-seconds:60}") long blockSeconds
    ) {
        return new LoginRateLimitService(
                maxFailures,
                Duration.ofSeconds(windowSeconds),
                Duration.ofSeconds(blockSeconds),
                Clock.systemUTC()
        );
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${app.student-identity.oidc.enabled:false}") boolean oidcEnabled,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            ObjectProvider<StudentOidcUserService> studentOidcUsers
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(new CookieCsrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/join/**", "/api/learn/**", "/api/library/shares/preview")
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, error) -> response.sendError(401))
                        .accessDeniedHandler((request, response, error) -> response.sendError(403))
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER
                        ))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/health/**", "/api/auth/csrf", "/api/auth/login", "/api/join/**", "/api/learn/**", "/api/projector/**", "/ws/**").permitAll()
                        .requestMatchers("/api/student-oidc/authorization/**", "/api/student-oidc/callback/**", "/api/student-account/providers").permitAll()
                        // ADR-0076 D7: public preview of a share token (POST body); exists only with the flag ON.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/library/shares/preview").permitAll()
                        .requestMatchers("/api/student-account/**").hasRole("STUDENT")
                        .requestMatchers("/api/**").hasRole("TEACHER")
                        .anyRequest().permitAll()
                );
        if (oidcEnabled) {
            ClientRegistrationRepository registrations = clientRegistrations.getObject();
            var resolver = new DefaultOAuth2AuthorizationRequestResolver(
                    registrations, "/api/student-oidc/authorization");
            // S256 PKCE even for confidential clients, in addition to the session-bound state and OIDC nonce.
            resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
            http.oauth2Login(oidc -> oidc
                    .loginPage("/learn")
                    .authorizationEndpoint(authorization -> authorization
                            .baseUri("/api/student-oidc/authorization")
                            .authorizationRequestResolver(resolver))
                    .redirectionEndpoint(redirection -> redirection
                            .baseUri("/api/student-oidc/callback/*"))
                    .userInfoEndpoint(userInfo -> userInfo
                            .oidcUserService(studentOidcUsers.getObject()))
                    .successHandler(new SimpleUrlAuthenticationSuccessHandler("/learn?connected=1"))
                    .failureHandler(new SimpleUrlAuthenticationFailureHandler("/learn?error=oidc"))
            );
        }
        return http.build();
    }
}
