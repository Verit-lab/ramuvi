import { apiFetch } from "./auth-api";

/** Teacher-only; does not expose any invitation until POST succeeds. */
export type IssuedEnrollmentInvite = {
  inviteId: string;
  enrollmentId: string;
  token: string;
  expiresAt: string;
};

export async function invitationIssuanceEnabled(): Promise<boolean> {
  const response = await apiFetch("/api/student-account-invitations/capabilities");
  if (!response.ok) return false;
  const view = (await response.json()) as { enabled?: unknown };
  return view.enabled === true;
}

export async function issueEnrollmentInvitation(enrollmentId: string): Promise<IssuedEnrollmentInvite> {
  const response = await apiFetch(
    `/api/student-account-invitations/enrollments/${encodeURIComponent(enrollmentId)}`,
    { method: "POST" },
  );
  if (!response.ok) {
    throw new Error(response.status === 404
      ? "Convites de conta não estão habilitados."
      : "Não foi possível criar o convite. Confira a matrícula e tente novamente.");
  }
  const invite = (await response.json()) as IssuedEnrollmentInvite;
  if (!/^[A-Za-z0-9_-]{43}$/.test(invite.token)) {
    throw new Error("A API retornou um convite inválido.");
  }
  return invite;
}

export async function revokeEnrollmentInvitation(inviteId: string): Promise<void> {
  const response = await apiFetch(
    `/api/student-account-invitations/${encodeURIComponent(inviteId)}`,
    { method: "DELETE" },
  );
  if (!response.ok) throw new Error("Não foi possível revogar o convite.");
}
