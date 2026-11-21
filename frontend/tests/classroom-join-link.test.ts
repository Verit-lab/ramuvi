import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  captureJoinLinkFromLocation,
  clearPendingJoinLink,
  joinResultMessage,
  normalizeJoinLinkToken,
} from "../lib/student-oidc-api";
import { joinLinkUrl } from "../lib/classroom-join-link-api";

const read = (file: string) => readFileSync(resolve(process.cwd(), file), "utf8");
const TOKEN = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

function fakeWindow(hash: string) {
  const storage = new Map<string, string>();
  const replaced: string[] = [];
  (globalThis as unknown as { window: unknown }).window = {
    location: { hash, pathname: "/learn", search: "?account=1" },
    history: { replaceState: (_: unknown, __: string, url: string) => replaced.push(url) },
    sessionStorage: {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    },
  };
  return { storage, replaced };
}

test("o token do link aceita grupos, minúsculas e espaços, e rejeita o código administrativo", () => {
  assert.equal(normalizeJoinLinkToken("abcd-efgh ijkl-mnop-qrst-uvwx-yz23-4567"), TOKEN);
  assert.equal(normalizeJoinLinkToken("TURMA2026"), null);
  assert.equal(normalizeJoinLinkToken(TOKEN + "A"), null);
  assert.equal(normalizeJoinLinkToken("ABCDEFGHIJKLMNOPQRSTUVWXYZ234561"), null); // 1 is not base32
});

test("o fragmento #ingresso sai da barra de endereço e fica só na aba até ser usado", () => {
  const { storage, replaced } = fakeWindow("#ingresso=" + TOKEN.toLowerCase());
  assert.equal(captureJoinLinkFromLocation(), TOKEN);
  assert.deepEqual(replaced, ["/learn?account=1"]);
  assert.equal([...storage.values()][0], TOKEN);
  clearPendingJoinLink();
  assert.equal(storage.size, 0);
  const invalid = fakeWindow("#ingresso=TURMA2026");
  assert.equal(captureJoinLinkFromLocation(), null);
  assert.equal(invalid.storage.size, 0);
});

test("mensagens não prometem Jornadas bloqueadas nem XP sem prova", () => {
  const blocked = joinResultMessage({ status: "JOINED_NEW_STUDENT", classroomName: "T", journeysAvailable: false });
  assert.match(blocked, /quando o professor liberar o acesso a estudos/);
  assert.doesNotMatch(blocked, /pontuações e progresso/);
  assert.match(joinResultMessage({ status: "LINKED_EXISTING_STUDENT", classroomName: "T", journeysAvailable: true }),
    /pontuações e progresso/);
  const pending = joinResultMessage({ status: "PENDING_APPROVAL", classroomName: "T", journeysAvailable: true });
  assert.match(pending, /Nenhum cadastro foi criado ou vinculado/);
  assert.doesNotMatch(pending, /Jornadas publicadas/);
});

test("o link compartilhado carrega o segredo apenas no fragmento", () => {
  const url = new URL(joinLinkUrl("https://ramuvi.example", {
    token: TOKEN, path: "/learn?account=1#ingresso=" + TOKEN, expiresAt: "", requiresApproval: false,
  }));
  assert.equal(url.search, "?account=1");
  assert.equal(url.hash, "#ingresso=" + TOKEN);
});

test("UI: confirmação explícita, retry, convite como exceção e sem classrooms.code", () => {
  const student = read("components/student/StudentAccountAccess.tsx");
  assert.match(student, /Confirmar e entrar na turma/);
  assert.match(student, /Tentar novamente/);
  assert.match(student, /Tenho um convite individual do professor/);
  assert.doesNotMatch(student, /classCode|joinStudentClassroom/);
  // Preview runs automatically; accept only from the confirm button.
  assert.equal((student.match(/acceptClassroomLink\(/g) ?? []).length, 1);
  const api = read("lib/student-oidc-api.ts");
  assert.match(api, /body: JSON\.stringify\(\{ token \}\)/);
  assert.doesNotMatch(api, /classrooms\/join/);
  const teacher = read("components/teacher/ClassroomJoinLinkPanel.tsx");
  assert.match(teacher, /classroomJoinLinksEnabled/);
  assert.match(teacher, /mostrado só agora/);
  assert.doesNotMatch(teacher, /localStorage|sessionStorage/);
  assert.doesNotMatch(read("components/teacher/ClassroomManagementPage.tsx"), /selfEnrollment|classCode/);
});

test("Jornadas: nenhum formulário de nome/matrícula para liberar estudo", () => {
  const page = read("app/learn/journeys/page.tsx");
  assert.doesNotMatch(page, /bootstrapJourneyRedemption|Matrícula, e-mail ou nome/);
  assert.doesNotMatch(read("lib/study-api.ts"), /redeem\/bootstrap/);
});
