package br.com.arenadev.config;

import java.util.Arrays;

/**
 * Startup guard for the single teacher credential. The development default
 * ({@value #DEVELOPMENT_DEFAULT_PASSWORD}) is public (application.yml,
 * compose.yaml, README), so a production instance that inherits it would
 * accept a well-known password. Outside production nothing changes, which
 * keeps local, CI and e2e setups working.
 */
final class TeacherCredentialPolicy {
    static final String DEVELOPMENT_DEFAULT_PASSWORD = "arena-dev-change-me";
    static final String PRODUCTION_PROFILE = "prod";

    private TeacherCredentialPolicy() {
    }

    static void validate(String[] activeProfiles, String password) {
        boolean production = activeProfiles != null
                && Arrays.stream(activeProfiles).anyMatch(PRODUCTION_PROFILE::equalsIgnoreCase);
        if (!production) return;
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("APP_TEACHER_PASSWORD não pode ficar vazio no perfil prod.");
        }
        if (DEVELOPMENT_DEFAULT_PASSWORD.equals(password.trim())) {
            throw new IllegalStateException(
                    "APP_TEACHER_PASSWORD usa a senha padrão de desenvolvimento; defina uma senha própria para o perfil prod.");
        }
    }
}
