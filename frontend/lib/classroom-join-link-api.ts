import { apiFetch } from "./auth-api";

/**
 * ADR-0074 teacher client. The link token exists only in the POST response and in
 * component state until the teacher copies it; it is never persisted in the browser
 * and never returned by status/list endpoints.
 */
export const JOIN_LINK_FRAGMENT = "ingresso";
const TOKEN = /^[A-Z2-7]{32}$/;

export type JoinLinkCapabilities = { classroomJoinLinksEnabled: boolean; autoEmailLinkEnabled: boolean };
export type JoinLinkStatus = {
  active: boolean;
  createdAt: string | null;
  expiresAt: string | null;
  expired: boolean;
  requiresApproval: boolean;
  acceptedCount: number;
  lastAcceptedAt: string | null;
  pendingRequests: number;
};
export type IssuedJoinLink = { token: string; path: string; expiresAt: string; requiresApproval: boolean };
export type JoinRequestReason = "APPROVAL_REQUIRED" | "POSSIBLE_DUPLICATE" | "EMAIL_REVIEW";
export type PendingJoinRequest = {
  requestId: string;
  reason: JoinRequestReason;
  displayName: string;
  verifiedEmail: string | null;
  createdAt: string;
  accountAlreadyLinked: boolean;
  emailMatchStudentId: string | null;
};
export type JoinLinkCandidate = {
  studentId: string;
  name: string;
  registration: string | null;
  preferredName: string | null;
  hasEmail: boolean;
};
export type JoinReviewQueue = { requests: PendingJoinRequest[]; candidates: JoinLinkCandidate[] };

export async function fetchJoinLinkCapabilities(): Promise<JoinLinkCapabilities> {
  const response = await apiFetch("/api/student-account-invitations/capabilities");
  if (!response.ok) return { classroomJoinLinksEnabled: false, autoEmailLinkEnabled: false };
  const view = (await response.json()) as Partial<JoinLinkCapabilities>;
  return {
    classroomJoinLinksEnabled: view.classroomJoinLinksEnabled === true,
    autoEmailLinkEnabled: view.autoEmailLinkEnabled === true,
  };
}

async function fail(response: Response, fallback: string): Promise<never> {
  let message = fallback;
  try {
    const body = (await response.json()) as { message?: unknown };
    if (typeof body.message === "string" && body.message) message = body.message;
  } catch { /* keep fallback */ }
  throw new Error(response.status === 404 ? "O link de ingresso não está habilitado nesta instalação." : message);
}

const base = (classroomId: string) => `/api/classrooms/${encodeURIComponent(classroomId)}`;

export async function fetchJoinLinkStatus(classroomId: string): Promise<JoinLinkStatus> {
  const response = await apiFetch(`${base(classroomId)}/join-link`);
  if (!response.ok) return fail(response, "Não foi possível consultar o link de ingresso.");
  return (await response.json()) as JoinLinkStatus;
}

export async function issueJoinLink(classroomId: string, validDays: number, requiresApproval: boolean): Promise<IssuedJoinLink> {
  const response = await apiFetch(`${base(classroomId)}/join-link`, {
    method: "POST",
    body: JSON.stringify({ validDays, requiresApproval }),
  });
  if (!response.ok) return fail(response, "Não foi possível gerar o link de ingresso.");
  const issued = (await response.json()) as IssuedJoinLink;
  if (!TOKEN.test(issued.token) || issued.path !== `/learn?account=1#${JOIN_LINK_FRAGMENT}=${issued.token}`) {
    throw new Error("A API retornou um link de ingresso inválido.");
  }
  return issued;
}

/** Absolute URL; the secret travels in the fragment, which browsers never send to servers. */
export function joinLinkUrl(origin: string, issued: IssuedJoinLink): string {
  return new URL(issued.path, origin).toString();
}

export async function setJoinLinkApproval(classroomId: string, requiresApproval: boolean): Promise<JoinLinkStatus> {
  const response = await apiFetch(`${base(classroomId)}/join-link`, {
    method: "PATCH",
    body: JSON.stringify({ requiresApproval }),
  });
  if (!response.ok) return fail(response, "Não foi possível alterar a aprovação.");
  return (await response.json()) as JoinLinkStatus;
}

export async function revokeJoinLink(classroomId: string): Promise<void> {
  const response = await apiFetch(`${base(classroomId)}/join-link`, { method: "DELETE" });
  if (!response.ok) return fail(response, "Não foi possível revogar o link.");
}

export async function fetchJoinReviewQueue(classroomId: string): Promise<JoinReviewQueue> {
  const response = await apiFetch(`${base(classroomId)}/join-requests`);
  if (!response.ok) return fail(response, "Não foi possível consultar os pedidos de ingresso.");
  return (await response.json()) as JoinReviewQueue;
}

export async function approveJoinRequest(
  classroomId: string, requestId: string, decision: { mode: "CREATE_NEW" } | { mode: "LINK_EXISTING"; studentId: string },
): Promise<void> {
  const response = await apiFetch(`${base(classroomId)}/join-requests/${encodeURIComponent(requestId)}/approve`, {
    method: "POST",
    body: JSON.stringify(decision),
  });
  if (!response.ok) return fail(response, "Não foi possível aprovar o pedido.");
}

export async function rejectJoinRequest(classroomId: string, requestId: string): Promise<void> {
  const response = await apiFetch(`${base(classroomId)}/join-requests/${encodeURIComponent(requestId)}/reject`, {
    method: "POST",
    body: "{}",
  });
  if (!response.ok) return fail(response, "Não foi possível recusar o pedido.");
}
