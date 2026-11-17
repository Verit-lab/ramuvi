package br.com.arenadev.identity.account;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StudentInvitationCapabilitiesControllerTest {
    @Test
    void capabilityRequiresAllThreeIndependentFeatureFlags() {
        assertThat(new StudentInvitationCapabilitiesController(true, true, true)
                .capabilities().getBody().enabled()).isTrue();
        assertThat(new StudentInvitationCapabilitiesController(false, true, true)
                .capabilities().getBody().enabled()).isFalse();
        assertThat(new StudentInvitationCapabilitiesController(true, false, true)
                .capabilities().getBody().enabled()).isFalse();
        assertThat(new StudentInvitationCapabilitiesController(true, true, false)
                .capabilities().getBody().enabled()).isFalse();
        assertThat(new StudentInvitationCapabilitiesController(true, true, true)
                .capabilities().getHeaders().getCacheControl()).contains("no-store");
    }
}
