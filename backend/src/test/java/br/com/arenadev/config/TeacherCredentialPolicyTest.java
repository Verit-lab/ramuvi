package br.com.arenadev.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeacherCredentialPolicyTest {
    @Test
    void productionRejectsThePublicDevelopmentPassword() {
        assertThatThrownBy(() -> TeacherCredentialPolicy.validate(new String[] {"prod"}, "arena-dev-change-me"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("senha padrão");
        assertThatThrownBy(() -> TeacherCredentialPolicy.validate(new String[] {"PROD"}, "  arena-dev-change-me "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionRejectsBlankPassword() {
        assertThatThrownBy(() -> TeacherCredentialPolicy.validate(new String[] {"prod"}, " "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TeacherCredentialPolicy.validate(new String[] {"prod"}, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionAcceptsAnOwnPassword() {
        assertThatCode(() -> TeacherCredentialPolicy.validate(new String[] {"prod"}, "uma-senha-propria"))
                .doesNotThrowAnyException();
    }

    @Test
    void nonProductionProfilesKeepTheDevelopmentDefault() {
        assertThatCode(() -> TeacherCredentialPolicy.validate(new String[0], "arena-dev-change-me"))
                .doesNotThrowAnyException();
        assertThatCode(() -> TeacherCredentialPolicy.validate(new String[] {"test"}, "arena-dev-change-me"))
                .doesNotThrowAnyException();
        assertThatCode(() -> TeacherCredentialPolicy.validate(null, "arena-dev-change-me"))
                .doesNotThrowAnyException();
    }
}
