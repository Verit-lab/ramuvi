import { apiFetch } from "./auth-api";
import { studentAccessStorageKey } from "./app-state";
import type { StudentJoinAccess } from "./realtime-api";

export type MemberLiveClass = {
  sessionId: string;
  classroomId: string;
  classroomName: string;
  sessionTitle: string;
  startedAt: string;
};

/** Participant access of ONE session, the same shape the /join runtime already restores. */
export type MemberJoinAccess = StudentJoinAccess;

export class MemberLiveError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = "MemberLiveError";
  }
}

/** ADR-0076 D4: discovery comes from the verified account; no code, no device token. */
export async function fetchMemberLiveClasses(): Promise<MemberLiveClass[]> {
  const response = await apiFetch("/api/student-account/live-sessions");
  if (!response.ok) {
    throw new MemberLiveError("Não foi possível consultar suas aulas ao vivo.", response.status);
  }
  return (await response.json()) as MemberLiveClass[];
}

/**
 * Enters the class without typing the code. The returned participant access is kept only in
 * this tab (sessionStorage, as /join does) and the caller navigates to the live runtime.
 */
export async function joinMemberLiveClass(sessionId: string): Promise<string> {
  const response = await apiFetch(`/api/student-account/live-sessions/${encodeURIComponent(sessionId)}/join`, {
    method: "POST",
    body: "{}",
  });
  if (response.status === 404) {
    throw new MemberLiveError("Esta aula foi encerrada ou não está mais disponível para sua conta.", 404);
  }
  if (!response.ok) {
    throw new MemberLiveError("Não foi possível entrar na aula. Tente novamente.", response.status);
  }
  const access = (await response.json()) as MemberJoinAccess;
  return storeMemberAccess(access);
}

export function storeMemberAccess(access: MemberJoinAccess, storage: Pick<Storage, "setItem"> = window.sessionStorage): string {
  storage.setItem(studentAccessStorageKey(access.code), JSON.stringify(access));
  return `/join?code=${encodeURIComponent(access.code)}`;
}
