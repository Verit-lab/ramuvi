package br.com.arenadev.identity.account;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StudentInvitationCapabilitiesControllerTest {
    @Test
    void capabilityRequiresAllThreeIndependentFeatureFlags() {
        assertThat(new StudentInvitationCapabilitiesController(true, true, true, false, false)
                .capabilities().getBody().enabled()).isTrue();
        assertThat(new StudentInvitationCapabilitiesController(false, true, true, false, false)
                .capabilities().getBody().enabled()).isFalse();
        assertThat(new StudentInvitationCapabilitiesController(true, false, true, false, false)
                .capabilities().getBody().enabled()).isFalse();
        assertThat(new StudentInvitationCapabilitiesController(true, true, false, false, false)
                .capabilities().getBody().enabled()).isFalse();
        assertThat(new StudentInvitationCapabilitiesController(true, true, true, false, false)
                .capabilities().getHeaders().getCacheControl()).contains("no-store");
    }

    /** UI guard must match ClassroomJoinLinkController's @ConditionalOnProperty exactly. */
    @Test
    void joinLinkCapabilityMatchesBackendConditionAndAutoEmailNeedsLinks() {
        var on = new StudentInvitationCapabilitiesController(false, true, true, true, true).capabilities().getBody();
        assertThat(on.classroomJoinLinksEnabled()).isTrue();
        assertThat(on.autoEmailLinkEnabled()).isTrue();
        assertThat(new StudentInvitationCapabilitiesController(false, true, false, true, true)
                .capabilities().getBody().classroomJoinLinksEnabled()).isFalse();
        assertThat(new StudentInvitationCapabilitiesController(false, false, true, true, true)
                .capabilities().getBody().classroomJoinLinksEnabled()).isFalse();
        var noLinks = new StudentInvitationCapabilitiesController(true, true, true, false, true).capabilities().getBody();
        assertThat(noLinks.classroomJoinLinksEnabled()).isFalse();
        assertThat(noLinks.autoEmailLinkEnabled()).isFalse();
    }
}
