package br.com.arenadev.journey;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JourneyControllerTest {
    @Test
    void createReturnsCreatedJourney() {
        JourneyService service = mock(JourneyService.class);
        UUID classroomId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        JourneyService.JourneyInput input = new JourneyService.JourneyInput(classroomId, "Jornada", null, List.of());
        JourneyService.JourneyView view = new JourneyService.JourneyView(
                journeyId, classroomId, "Jornada", null, JourneyStatus.DRAFT, List.of(), Instant.now(), Instant.now(), false, null
        );
        when(service.create(input)).thenReturn(view);

        JourneyController controller = new JourneyController(service, mock(JourneyReconstructionService.class));
        JourneyService.JourneyView result = controller.create(input);

        assertThat(result.id()).isEqualTo(journeyId);
        assertThat(result.status()).isEqualTo(JourneyStatus.DRAFT);
        verify(service).create(input);
    }

    @Test
    void overwriteEndpointsRequireBackendPreviewAndTypedConfirmation() {
        JourneyService service = mock(JourneyService.class);
        JourneyReconstructionService reconstruction = mock(JourneyReconstructionService.class);
        JourneyController controller = new JourneyController(service, reconstruction);
        UUID id = UUID.randomUUID();
        var input = new JourneyService.MarkdownInput("## Bloco", Instant.now());
        var confirmed = new JourneyReconstructionService.OverwriteInput(
                input.markdown(), input.expectedUpdatedAt(), "Jornada original");
        controller.overwritePreview(id, input);
        controller.overwrite(id, confirmed);
        verify(reconstruction).overwritePreview(id, input);
        verify(reconstruction).overwrite(id, confirmed);
    }

    @Test
    void lifecycleEndpointsDelegateToJourneyService() {
        JourneyService service = mock(JourneyService.class);
        UUID id = UUID.randomUUID();
        JourneyController controller = new JourneyController(service, mock(JourneyReconstructionService.class));

        controller.publish(id);
        controller.archive(id);

        verify(service).publish(id);
        verify(service).archive(id);
    }
}
