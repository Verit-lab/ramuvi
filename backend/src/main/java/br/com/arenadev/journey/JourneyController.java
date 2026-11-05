package br.com.arenadev.journey;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/journeys")
public class JourneyController {
    private final JourneyService service;
    private final JourneyReconstructionService reconstruction;

    public JourneyController(JourneyService service, JourneyReconstructionService reconstruction) {
        this.service = service;
        this.reconstruction = reconstruction;
    }

    @GetMapping
    public List<JourneyService.JourneyView> list(@RequestParam UUID classroomId) {
        return service.list(classroomId);
    }

    @GetMapping("/{id}")
    public JourneyService.JourneyView get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JourneyService.JourneyView create(@RequestBody JourneyService.JourneyInput input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public JourneyService.JourneyView update(@PathVariable UUID id, @RequestBody JourneyService.JourneyInput input) {
        return service.update(id, input);
    }

    @PatchMapping("/{id}/release")
    public JourneyService.JourneyView release(@PathVariable UUID id, @RequestBody ReleaseInput input) {
        return service.changeRelease(id, input == null ? null : input.releasedChapterCount());
    }

    public record ReleaseInput(Integer releasedChapterCount) {}

    @PostMapping("/{id}/publish")
    public JourneyService.JourneyView publish(@PathVariable UUID id) { return service.publish(id); }

    @PostMapping("/{id}/archive")
    public JourneyService.JourneyView archive(@PathVariable UUID id) { return service.archive(id); }

    @PostMapping("/{id}/stages/{stageId}/refresh-resource")
    public JourneyService.JourneyView refreshStageResource(
            @PathVariable UUID id,
            @PathVariable UUID stageId
    ) {
        return service.refreshStageResource(id, stageId);
    }

    @GetMapping("/{id}/stages/{stageId}/content")
    public JourneyService.StageContentView stageContent(@PathVariable UUID id, @PathVariable UUID stageId) {
        return service.stageContent(id, stageId);
    }

    @PutMapping("/{id}/stages/{stageId}/content")
    public JourneyService.JourneyView updateStageContent(
            @PathVariable UUID id,
            @PathVariable UUID stageId,
            @RequestBody JourneyService.StageContentInput input
    ) {
        return service.updateStageContent(id, stageId, input);
    }

    @GetMapping("/{id}/markdown")
    public JourneyService.MarkdownView markdown(@PathVariable UUID id) { return service.markdown(id); }

    @PutMapping("/{id}/markdown")
    public JourneyService.JourneyView updateMarkdown(@PathVariable UUID id, @RequestBody JourneyService.MarkdownInput input) {
        return service.updateMarkdown(id, input);
    }

    @PostMapping("/{id}/markdown/reconstruction-preview")
    public JourneyReconstructionService.ReconstructionPreview reconstructionPreview(
            @PathVariable UUID id, @RequestBody JourneyService.MarkdownInput input) {
        return reconstruction.preview(id, input);
    }

    @PutMapping("/{id}/markdown/reconstruct")
    public JourneyService.JourneyView reconstruct(
            @PathVariable UUID id, @RequestBody JourneyService.MarkdownInput input) {
        return reconstruction.rebuild(id, input);
    }

    @PostMapping("/{id}/markdown/overwrite-preview")
    public JourneyReconstructionService.OverwritePreview overwritePreview(
            @PathVariable UUID id, @RequestBody JourneyService.MarkdownInput input) {
        return reconstruction.overwritePreview(id, input);
    }

    @PutMapping("/{id}/markdown/overwrite")
    public JourneyReconstructionService.OverwriteResult overwrite(
            @PathVariable UUID id, @RequestBody JourneyReconstructionService.OverwriteInput input) {
        return reconstruction.overwrite(id, input);
    }

    @GetMapping("/{id}/progress/{enrollmentId}")
    public JourneyService.JourneyProgressView progress(@PathVariable UUID id, @PathVariable UUID enrollmentId) {
        return service.progress(id, enrollmentId);
    }

    @PostMapping("/{id}/stages/{stageId}/progress/{enrollmentId}/start")
    public JourneyService.JourneyProgressView startStage(@PathVariable UUID id, @PathVariable UUID stageId, @PathVariable UUID enrollmentId) {
        return service.startStage(id, stageId, enrollmentId);
    }

    @PostMapping("/{id}/stages/{stageId}/progress/{enrollmentId}/complete")
    public JourneyService.JourneyProgressView completeStage(@PathVariable UUID id, @PathVariable UUID stageId, @PathVariable UUID enrollmentId) {
        return service.completeStage(id, stageId, enrollmentId);
    }
}
