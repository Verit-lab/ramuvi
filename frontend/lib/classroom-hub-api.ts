import { apiFetch } from "./auth-api";
import { apiRequest } from "./http/client";
import { HttpApiError } from "./http/errors";
import type { ClassroomHub } from "./classroom-hub";

export type TeacherCapabilities = { classroomHub: boolean; librarySharing: boolean };

export type HubSessionView = {
  id: string;
  classroomId: string;
  classroomName: string;
  title: string;
  status: "ACTIVE" | "FINISHED";
  startedAt: string;
  endedAt: string | null;
};

export type ActiveSessionConflictCode = "ACTIVE_SESSION_EXISTS" | "ACTIVE_SESSION_CHANGED";

/** Typed 409 of ADR-0076 D3: the server says which session is actually active. */
export class ActiveSessionConflictError extends HttpApiError {
  constructor(
    message: string,
    public readonly code: ActiveSessionConflictCode,
    public readonly activeSession: HubSessionView | null,
  ) {
    super(message, 409);
    this.name = "ActiveSessionConflictError";
  }
}

const DISABLED: TeacherCapabilities = { classroomHub: false, librarySharing: false };

/** Older backends (404) or any failure keep the v0.12.1 screens: fail closed. */
export async function fetchTeacherCapabilities(): Promise<TeacherCapabilities> {
  try {
    const response = await apiFetch("/api/teacher/capabilities");
    if (!response.ok) return DISABLED;
    const body = (await response.json()) as Partial<TeacherCapabilities>;
    return { classroomHub: body.classroomHub === true, librarySharing: body.librarySharing === true };
  } catch {
    return DISABLED;
  }
}

export function fetchClassroomHub(classroomId: string): Promise<ClassroomHub> {
  return apiRequest<ClassroomHub>(`/api/classrooms/${encodeURIComponent(classroomId)}/hub`, undefined, {
    errorMessage: "Não foi possível carregar a central da turma.",
  });
}

async function sessionMutation<T>(path: string, body: unknown): Promise<T> {
  const response = await apiFetch(path, { method: "POST", body: JSON.stringify(body) });
  if (response.status === 409) {
    const payload = (await response.json().catch(() => ({}))) as {
      message?: string;
      code?: ActiveSessionConflictCode;
      activeSession?: HubSessionView | null;
    };
    throw new ActiveSessionConflictError(
      payload.message ?? "A turma já tem uma aula ativa.",
      payload.code === "ACTIVE_SESSION_CHANGED" ? "ACTIVE_SESSION_CHANGED" : "ACTIVE_SESSION_EXISTS",
      payload.activeSession ?? null,
    );
  }
  if (!response.ok) {
    const payload = (await response.json().catch(() => ({}))) as { message?: string };
    throw new HttpApiError(payload.message ?? `Falha ao atualizar a aula (${response.status}).`, response.status);
  }
  return (await response.json()) as T;
}

export function startClassroomSession(classroomId: string, title: string | null): Promise<HubSessionView> {
  return sessionMutation<HubSessionView>(`/api/classrooms/${encodeURIComponent(classroomId)}/sessions`, {
    title,
  });
}

/** Finishes the session the teacher SAW and starts another, only with explicit confirmation. */
export function switchClassroomSession(
  classroomId: string,
  expectedActiveSessionId: string,
  title: string | null,
): Promise<{ finished: HubSessionView; started: HubSessionView }> {
  return sessionMutation(`/api/classrooms/${encodeURIComponent(classroomId)}/sessions/switch`, {
    expectedActiveSessionId,
    title,
    confirm: true,
  });
}
