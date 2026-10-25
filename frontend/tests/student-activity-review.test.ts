import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { studentActivityReview } from "../lib/student-activity-review";
import type { ParticipantActivity } from "../lib/participant-submission-api";

function activity(status: ParticipantActivity["submissionStatus"], feedback: string | null = null, publishedAt: string | null = null) {
  return { submissionStatus: status, publishedFeedback: feedback, feedbackPublishedAt: publishedAt };
}

test("no feedback view exists before submission", () => {
  assert.equal(studentActivityReview(activity("NOT_STARTED")), null);
  assert.equal(studentActivityReview(activity("IN_PROGRESS")), null);
});

test("submitted and under-review attempts never reveal unapproved draft feedback", () => {
  assert.match(studentActivityReview(activity("SUBMITTED"))?.pendingMessage ?? "", /Aguarde a avaliação/);
  assert.match(studentActivityReview(activity("UNDER_REVIEW"))?.pendingMessage ?? "", /professor está avaliando/);
  assert.equal(studentActivityReview(activity("GRADED", "Rascunho confidencial", null))?.publishedFeedback, null);
  assert.equal(studentActivityReview(activity("RETURNED", "   ", "2026-10-25T10:00:00Z"))?.publishedFeedback, null);
});

test("an explicitly published teacher feedback becomes available, without computing a score", () => {
  const review = studentActivityReview(activity("RETURNED", "  Reveja a função main.  ", "2026-10-25T10:00:00Z"));
  assert.equal(review?.title, "Devolutiva do professor disponível");
  assert.equal(review?.publishedFeedback, "Reveja a função main.");
  assert.equal(review?.pendingMessage, null);
});

test("the runner renders delivered feedback as plain text and still allows read-only review", () => {
  const runner = readFileSync(resolve(process.cwd(), "components/StudentActivitiesPanel.tsx"), "utf8");
  assert.match(runner, /studentActivityReview\(open\)/);
  assert.match(runner, /review\.publishedFeedback/);
  assert.match(runner, /Atualizar devolutiva/);
  assert.match(runner, /!editable/);
  assert.doesNotMatch(runner, /dangerouslySetInnerHTML/);
});
