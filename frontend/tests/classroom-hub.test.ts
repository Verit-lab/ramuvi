import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  attendanceLabel,
  describeDuration,
  hubPrimaryAction,
  normalizeSessionTitle,
  suggestedSessionTitle,
  type HubSessionSummary,
} from "../lib/classroom-hub";
import {
  ActiveSessionConflictError,
  fetchTeacherCapabilities,
  startClassroomSession,
  switchClassroomSession,
} from "../lib/classroom-hub-api";

const read = (file: string) => readFileSync(resolve(process.cwd(), file), "utf8");

function session(overrides: Partial<HubSessionSummary> = {}): HubSessionSummary {
  return {
    id: "s1", title: "Aula de 20/11", status: "ACTIVE", startedAt: "2026-11-20T12:00:00Z", endedAt: null,
    participantCount: 30, presentCount: 12, connectedCount: 10, activeMinutes: 25, stale: false, ...overrides,
  };
}

type Call = { url: string; init?: RequestInit };
function mockFetch(responses: Array<{ status: number; body: unknown }>) {
  const calls: Call[] = [];
  (globalThis as unknown as { fetch: unknown }).fetch = async (url: string, init?: RequestInit) => {
    calls.push({ url, init });
    if (url.endsWith("/api/auth/csrf")) {
      return new Response(JSON.stringify({ token: "csrf-hub" }), { status: 200 });
    }
    const next = responses.shift() ?? { status: 500, body: {} };
    return new Response(JSON.stringify(next.body), { status: next.status, headers: { "Content-Type": "application/json" } });
  };
  return calls;
}

test("título sugerido usa dia/mês e pode ser editado; vazio vira padrão do servidor", () => {
  assert.equal(suggestedSessionTitle(new Date(2026, 10, 5)), "Aula de 05/11");
  assert.equal(normalizeSessionTitle("   "), null);
  assert.equal(normalizeSessionTitle("  Aula   de  revisão "), "Aula de revisão");
  assert.equal(normalizeSessionTitle("x".repeat(200))?.length, 160);
});

test("ação principal: iniciar, retomar, aula esquecida ou turma inativa — nunca encerra sozinha", () => {
  const classroom = { id: "c1", name: "Turma", active: true };
  assert.equal(hubPrimaryAction({ classroom, activeSession: null }).kind, "start");
  assert.equal(hubPrimaryAction({ classroom, activeSession: session() }).kind, "resume");
  const stale = hubPrimaryAction({ classroom, activeSession: session({ stale: true, activeMinutes: 60 * 50 }) });
  assert.equal(stale.kind, "stale");
  assert.equal(stale.label, "Retomar aula");
  assert.match(stale.hint, /há 2 dias/);
  assert.equal(hubPrimaryAction({ classroom: { ...classroom, active: false }, activeSession: null }).kind, "disabled");
});

test("presença é por encontro, com denominador da própria aula", () => {
  assert.equal(attendanceLabel(session()), "12 de 30 presente(s)");
  assert.equal(attendanceLabel(session({ participantCount: 0, presentCount: 0 })), "Sem participantes");
  assert.equal(describeDuration(0), "agora");
  assert.equal(describeDuration(45), "há 45 min");
  assert.equal(describeDuration(180), "há 3 h");
});

test("409 tipado expõe a aula realmente ativa", async () => {
  const active = { id: "s-ativa", classroomId: "c1", classroomName: "Turma", title: "Aula antiga", status: "ACTIVE", startedAt: "2026-11-19T10:00:00Z", endedAt: null };
  const calls = mockFetch([{ status: 409, body: { message: "Já existe uma aula ativa", code: "ACTIVE_SESSION_EXISTS", activeSession: active } }]);
  await assert.rejects(startClassroomSession("c1", "Nova"), (error: unknown) => {
    assert.ok(error instanceof ActiveSessionConflictError);
    assert.equal(error.code, "ACTIVE_SESSION_EXISTS");
    assert.equal(error.activeSession?.id, "s-ativa");
    return true;
  });
  const post = calls.find((call) => call.url.endsWith("/api/classrooms/c1/sessions"));
  assert.equal(new Headers(post?.init?.headers).get("X-XSRF-TOKEN"), "csrf-hub");
});

test("trocar de aula envia a aula vista pelo professor e a confirmação explícita", async () => {
  const calls = mockFetch([{ status: 201, body: { finished: { id: "s1" }, started: { id: "s2" } } }]);
  const result = await switchClassroomSession("c1", "s1", "Aula 2");
  assert.equal(result.started.id, "s2");
  const post = calls.find((call) => call.url.endsWith("/sessions/switch"));
  assert.deepEqual(JSON.parse(String(post?.init?.body)), { expectedActiveSessionId: "s1", title: "Aula 2", confirm: true });
});

test("capacidades falham fechadas quando o backend não tem a rota", async () => {
  mockFetch([{ status: 404, body: {} }]);
  assert.deepEqual(await fetchTeacherCapabilities(), { classroomHub: false, librarySharing: false });
  mockFetch([{ status: 200, body: { classroomHub: true, librarySharing: "yes" } }]);
  assert.deepEqual(await fetchTeacherCapabilities(), { classroomHub: true, librarySharing: false });
});

test("a central não encerra aula por tempo e pede confirmação antes de trocar", () => {
  const source = read("components/teacher/ClassroomSessionHub.tsx");
  assert.doesNotMatch(source, /setTimeout|setInterval/);
  assert.match(source, /role="dialog"/);
  assert.match(source, /Encerrar e iniciar outra/);
  assert.match(source, /switchClassroomSession\(classroomId, active\.id/);
});
