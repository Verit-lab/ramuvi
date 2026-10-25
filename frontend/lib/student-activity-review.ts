import type { ParticipantActivity } from "./participant-submission-api";

type ReviewableActivity = Pick<ParticipantActivity, "submissionStatus" | "publishedFeedback" | "feedbackPublishedAt">;

/** Display only the teacher's already-published feedback. The backend enforces visibility. */
export function studentActivityReview(activity: ReviewableActivity) {
  const status = activity.submissionStatus;
  if (status === "NOT_STARTED" || status === "IN_PROGRESS") return null;
  const publishedFeedback = activity.feedbackPublishedAt && activity.publishedFeedback?.trim()
    ? activity.publishedFeedback.trim()
    : null;
  const pendingMessage = status === "SUBMITTED"
    ? "Sua entrega foi registrada. Aguarde a avaliação do professor."
    : status === "UNDER_REVIEW"
      ? "O professor está avaliando sua entrega."
      : "A avaliação foi registrada. A devolutiva ainda não foi publicada.";
  return {
    title: publishedFeedback ? "Devolutiva do professor disponível" : "Atividade entregue",
    pendingMessage: publishedFeedback ? null : pendingMessage,
    publishedFeedback,
  };
}
