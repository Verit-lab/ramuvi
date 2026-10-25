import type { ScoreEvent } from "./types";
import type { SessionEvent, SessionEventType } from "./session-event-api";

export type HistoryFilter = "all" | "xp" | "actions";
export type HistoryEntry =
  | { kind: "xp"; key: string; occurredAt: string; title: string; detail: string; category: string; score: ScoreEvent; studentName: string }
  | { kind: "actions"; key: string; occurredAt: string; title: string; detail: string; category: string; operation: SessionEvent };

function operationalCategory(type: SessionEventType) {
  if (type.startsWith("SESSION_")) return "Sessão";
  if (type.startsWith("ARENA_") || type.startsWith("FLOW_")) return "Condução";
  if (type.startsWith("TIMER_")) return "Tempo";
  if (type === "GROUPS_ORGANIZED") return "Organização";
  return "Dinâmica";
}

/** A read-only projection: XP keeps its own auditable reversal contract. */
export function mergeClassroomHistory(
  scoreEvents: readonly ScoreEvent[],
  operationalEvents: readonly SessionEvent[],
  names: ReadonlyMap<string, string>,
): HistoryEntry[] {
  const xp: HistoryEntry[] = scoreEvents.map((score) => ({
    kind: "xp", key: `xp:${score.id}`, occurredAt: score.createdAt,
    title: names.get(score.studentId) ?? "Aluno removido",
    detail: score.description,
    category: score.reversalOf ? "Reversão" : score.reversed ? "Revertido" : score.category,
    score, studentName: names.get(score.studentId) ?? "Aluno removido",
  }));
  const operations: HistoryEntry[] = operationalEvents.map((operation) => ({
    kind: "actions", key: `action:${operation.id}`, occurredAt: operation.occurredAt,
    title: operation.summary,
    detail: `${operation.sessionTitle} · ${operation.actor === "TEACHER" ? "Professor" : "Sistema"}`,
    category: operationalCategory(operation.eventType),
    operation,
  }));
  return [...xp, ...operations].sort((a, b) => {
    const delta = new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime();
    return (Number.isNaN(delta) ? 0 : delta) || a.key.localeCompare(b.key);
  });
}

export function filterClassroomHistory(entries: readonly HistoryEntry[], filter: HistoryFilter, query: string): HistoryEntry[] {
  const needle = query.trim().toLocaleLowerCase("pt-BR");
  return entries.filter((entry) =>
    (filter === "all" || entry.kind === filter) &&
    (!needle || `${entry.title} ${entry.detail} ${entry.category} ${entry.kind === "xp" ? entry.score.points + " XP" : entry.operation.eventType}`
      .toLocaleLowerCase("pt-BR").includes(needle)),
  );
}
