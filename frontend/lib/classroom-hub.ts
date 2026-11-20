// v0.12.2 I1 (ADR-0076 D2/D3): pure presentation rules of the classroom hub.
// Kept free of React/fetch so they are unit-tested with node:test.

export type HubSessionSummary = {
  id: string;
  title: string;
  status: "ACTIVE" | "FINISHED";
  startedAt: string;
  endedAt: string | null;
  participantCount: number;
  presentCount: number;
  connectedCount: number;
  activeMinutes: number | null;
  stale: boolean;
};

export type ClassroomHub = {
  classroom: { id: string; name: string; active: boolean };
  activeSession: HubSessionSummary | null;
  recentSessions: HubSessionSummary[];
  totals: {
    activeStudents: number;
    publishedJourneys: number;
    draftJourneys: number;
    activities: number;
    sessions: number;
  };
};

export type HubPrimaryAction =
  | { kind: "disabled"; label: string; hint: string }
  | { kind: "start"; label: string; hint: string }
  | { kind: "resume"; label: string; hint: string }
  | { kind: "stale"; label: string; hint: string };

/** "Aula de 20/11" in the classroom's local calendar; the teacher can edit it before starting. */
export function suggestedSessionTitle(now: Date = new Date()): string {
  const day = String(now.getDate()).padStart(2, "0");
  const month = String(now.getMonth() + 1).padStart(2, "0");
  return `Aula de ${day}/${month}`;
}

export function hubPrimaryAction(hub: Pick<ClassroomHub, "classroom" | "activeSession">): HubPrimaryAction {
  if (!hub.classroom.active) {
    return {
      kind: "disabled",
      label: "Turma inativa",
      hint: "Reative a turma para iniciar aulas. O histórico continua disponível.",
    };
  }
  const active = hub.activeSession;
  if (!active) {
    return { kind: "start", label: "Iniciar aula", hint: "Cria um novo encontro com presença e dinâmicas próprias." };
  }
  if (active.stale) {
    return {
      kind: "stale",
      label: "Retomar aula",
      hint: `A aula “${active.title}” começou ${describeDuration(active.activeMinutes)} e continua aberta. Retome-a ou encerre-a para iniciar outra.`,
    };
  }
  return { kind: "resume", label: "Retomar aula", hint: `“${active.title}” está em andamento.` };
}

export function describeDuration(minutes: number | null | undefined): string {
  if (minutes == null || minutes < 0) return "há algum tempo";
  if (minutes < 1) return "agora";
  if (minutes < 60) return `há ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return `há ${hours} h`;
  return `há ${Math.floor(hours / 24)} dias`;
}

/** Attendance of ONE meeting; never a cumulative classroom counter (ADR-0076 D10). */
export function attendanceLabel(session: Pick<HubSessionSummary, "presentCount" | "participantCount">): string {
  if (session.participantCount === 0) return "Sem participantes";
  return `${session.presentCount} de ${session.participantCount} presente(s)`;
}

export function normalizeSessionTitle(raw: string): string | null {
  const title = raw.trim().replace(/\s+/g, " ");
  if (!title) return null;
  return title.length > 160 ? title.slice(0, 160) : title;
}
