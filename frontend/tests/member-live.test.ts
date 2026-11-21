import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fetchMemberLiveClasses, joinMemberLiveClass, storeMemberAccess, MemberLiveError } from "../lib/member-live-api";
import { fetchStudentCapabilities } from "../lib/student-capabilities-api";
import { studentAccessStorageKey } from "../lib/app-state";

const read = (file: string) => readFileSync(resolve(process.cwd(), file), "utf8");

type Call = { url: string; init?: RequestInit };
function mockFetch(responses: Array<{ status: number; body: unknown }>) {
  const calls: Call[] = [];
  (globalThis as unknown as { fetch: unknown }).fetch = async (url: string, init?: RequestInit) => {
    calls.push({ url, init });
    if (url.endsWith("/api/auth/csrf")) return new Response(JSON.stringify({ token: "csrf-member" }), { status: 200 });
    const next = responses.shift() ?? { status: 500, body: {} };
    return new Response(JSON.stringify(next.body), { status: next.status });
  };
  return calls;
}

function fakeWindow() {
  const storage = new Map<string, string>();
  (globalThis as unknown as { window: unknown }).window = {
    sessionStorage: { setItem: (key: string, value: string) => storage.set(key, value) },
    location: { origin: "http://localhost:3000" },
    dispatchEvent: () => true,
  };
  return storage;
}

const access = {
  token: "participant-token", code: "ABC234", sessionId: "s2", classroomName: "Turma", sessionTitle: "Aula 2",
  participantId: "p1", studentId: "st1", name: "Gabi", displayName: "Gabi", present: true,
  expiresAt: "2026-11-21T10:00:00Z", profileComplete: true,
};

test("membro entra na aula sem digitar código; acesso fica só na aba", async () => {
  const storage = fakeWindow();
  const calls = mockFetch([{ status: 200, body: access }]);
  const next = await joinMemberLiveClass("s2");
  assert.equal(next, "/join?code=ABC234");
  assert.equal(JSON.parse(storage.get(studentAccessStorageKey("ABC234")) ?? "{}").token, "participant-token");
  const post = calls.find((call) => call.url.endsWith("/api/student-account/live-sessions/s2/join"));
  assert.equal(post?.init?.method, "POST");
  assert.equal(new Headers(post?.init?.headers).get("X-XSRF-TOKEN"), "csrf-member");
  assert.equal(new Headers(post?.init?.headers).get("X-Study-Token"), null);
  assert.doesNotMatch(String(post?.init?.body), /student|account|enrollment/i);
});

test("aula encerrada ou de outra turma vira mensagem honesta, sem fallback por código", async () => {
  fakeWindow();
  mockFetch([{ status: 404, body: { message: "Aula não encontrada ou indisponível." } }]);
  await assert.rejects(joinMemberLiveClass("s-alheia"), (error: unknown) =>
    error instanceof MemberLiveError && error.status === 404 && /encerrada/.test(error.message));
  mockFetch([{ status: 401, body: {} }]);
  await assert.rejects(fetchMemberLiveClasses(), MemberLiveError);
});

test("capacidades do aluno falham fechadas sem sessão OIDC", async () => {
  fakeWindow();
  mockFetch([{ status: 401, body: {} }]);
  assert.deepEqual(await fetchStudentCapabilities(), { liveMemberJoin: false, personalLibrary: false });
  mockFetch([{ status: 200, body: { liveMemberJoin: true, personalLibrary: true } }]);
  assert.deepEqual(await fetchStudentCapabilities(), { liveMemberJoin: true, personalLibrary: true });
});

test("storeMemberAccess usa a mesma chave que o runtime de /join restaura", () => {
  const stored = new Map<string, string>();
  assert.equal(storeMemberAccess(access, { setItem: (k, v) => stored.set(k, v) }), "/join?code=ABC234");
  assert.ok(stored.has(studentAccessStorageKey("ABC234")));
});

test("o componente não pede código nem usa X-Study-Token", () => {
  const source = read("components/study/MemberLiveClasses.tsx");
  assert.doesNotMatch(source, /X-Study-Token|deviceClaim|joinCode/);
  assert.match(source, /não precisa digitar o código/);
});
