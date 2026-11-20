import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  LibraryAccessError,
  captureShareTokenFromLocation,
  clearPendingShareToken,
  normalizeShareToken,
  pendingShareToken,
  previewSharedMaterial,
  saveResultMessage,
  saveSharedMaterial,
} from "../lib/student-library-api";
import { createLibraryShare, fetchLibraryShareQr, permissionLabel, shareUrl } from "../lib/library-share-api";

const read = (file: string) => readFileSync(resolve(process.cwd(), file), "utf8");
const TOKEN = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

function fakeWindow(hash = "") {
  const storage = new Map<string, string>();
  const replaced: string[] = [];
  (globalThis as unknown as { window: unknown }).window = {
    location: { hash, pathname: "/learn/library/shared", search: "", origin: "http://localhost:3000" },
    history: { replaceState: (_: unknown, __: string, url: string) => replaced.push(url) },
    sessionStorage: {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    },
    dispatchEvent: () => true,
  };
  return { storage, replaced };
}

type Call = { url: string; init?: RequestInit };
function mockFetch(responses: Array<{ status: number; body: unknown }>) {
  const calls: Call[] = [];
  (globalThis as unknown as { fetch: unknown }).fetch = async (url: string, init?: RequestInit) => {
    calls.push({ url, init });
    if (url.endsWith("/api/auth/csrf")) return new Response(JSON.stringify({ token: "csrf-lib" }), { status: 200 });
    const next = responses.shift() ?? { status: 500, body: {} };
    return new Response(JSON.stringify(next.body), { status: next.status });
  };
  return calls;
}

test("token do fragmento sai da barra e só vai no corpo do POST", async () => {
  const { storage, replaced } = fakeWindow(`#acesso=${TOKEN.toLowerCase().replace(/(.{4})/g, "$1-").replace(/-$/, "")}`);
  assert.equal(captureShareTokenFromLocation(), TOKEN);
  assert.deepEqual(replaced, ["/learn/library/shared"]);
  assert.equal([...storage.values()][0], TOKEN);
  const calls = mockFetch([{ status: 200, body: { title: "Guia", permission: "VIEW", canSave: false } }]);
  await previewSharedMaterial(TOKEN);
  const preview = calls.find((call) => call.url.endsWith("/api/library/shares/preview"));
  assert.ok(preview);
  assert.doesNotMatch(preview.url, new RegExp(TOKEN));
  assert.equal(JSON.parse(String(preview.init?.body)).token, TOKEN);
  assert.equal(preview.init?.method, "POST");
});

test("fragmento inválido não é guardado e o pendente sobrevive ao login até ser usado", () => {
  const invalid = fakeWindow("#acesso=TURMA2026");
  assert.equal(captureShareTokenFromLocation(), null);
  assert.equal(invalid.storage.size, 0);
  assert.deepEqual(invalid.replaced, ["/learn/library/shared"]);
  const pending = fakeWindow("");
  pending.storage.set("ramuvi:library-share", TOKEN);
  assert.equal(captureShareTokenFromLocation(), TOKEN);
  assert.equal(pendingShareToken(), TOKEN);
  clearPendingShareToken();
  assert.equal(pendingShareToken(), null);
  assert.equal(normalizeShareToken("x".repeat(70)), null);
});

test("erros tipados: conta exigida, só visualização, indisponível e limite", async () => {
  fakeWindow();
  for (const [status, body, kind] of [
    [401, { code: "ACCOUNT_REQUIRED" }, "ACCOUNT_REQUIRED"],
    [403, { code: "VIEW_ONLY" }, "VIEW_ONLY"],
    [404, { code: "SHARE_UNAVAILABLE" }, "UNAVAILABLE"],
    [429, {}, "RATE_LIMITED"],
  ] as const) {
    mockFetch([{ status, body }]);
    await assert.rejects(saveSharedMaterial(TOKEN), (error: unknown) => error instanceof LibraryAccessError && error.kind === kind);
  }
});

test("salvar envia CSRF e nunca identificadores de conta ou aluno", async () => {
  fakeWindow();
  const calls = mockFetch([{ status: 201, body: { itemId: "i1", status: "SAVED", newerVersionAvailable: false } }]);
  const result = await saveSharedMaterial(TOKEN);
  assert.equal(saveResultMessage(result), "Material salvo na sua Biblioteca.");
  const post = calls.find((call) => call.url.endsWith("/api/student-account/library/items"));
  assert.equal(new Headers(post?.init?.headers).get("X-XSRF-TOKEN"), "csrf-lib");
  assert.deepEqual(Object.keys(JSON.parse(String(post?.init?.body))).sort(), ["replaceVersion", "token"]);
  assert.match(saveResultMessage({ itemId: "i1", status: "ALREADY_SAVED", newerVersionAvailable: true }), /outra versão/);
});

test("professor: link com fragmento, QR por POST e rótulos distintos de Jornada", async () => {
  fakeWindow();
  assert.equal(shareUrl("https://ramuvi.example/", "/learn/library/shared#acesso=" + TOKEN), "https://ramuvi.example/learn/library/shared#acesso=" + TOKEN);
  assert.equal(permissionLabel("SAVE"), "Permitir salvar na Minha Biblioteca");
  const calls = mockFetch([{ status: 201, body: { id: "s1", token: TOKEN, path: "/learn/library/shared#acesso=" + TOKEN } }]);
  await createLibraryShare("r1", { permission: "VIEW", validDays: 14, label: "  " });
  assert.deepEqual(JSON.parse(String(calls.at(-1)?.init?.body)), { permission: "VIEW", validDays: 14, label: null });
  const qrCalls = mockFetch([{ status: 200, body: {} }]);
  await fetchLibraryShareQr("http://localhost:3000", TOKEN);
  const qr = qrCalls.find((call) => call.url.endsWith("/api/library/shares/qr"));
  assert.equal(qr?.init?.method, "POST");
  assert.doesNotMatch(String(qr?.url), new RegExp(TOKEN));
});

test("UI: Compartilhar é ação própria, sem XP, e a página pública é noindex/no-referrer", () => {
  const workspace = read("components/LibraryWorkspace.tsx");
  assert.match(workspace, /Adicionar a uma Jornada/);
  assert.match(workspace, />Compartilhar</);
  const panel = read("components/LibrarySharePanel.tsx");
  assert.match(panel, /Não adiciona à Jornada nem apresenta na aula/);
  assert.doesNotMatch(panel + read("components/library/SharedMaterialPage.tsx"), /score-api|scoreEvents|awardXp|recordPresence/);
  const page = read("app/learn/library/shared/page.tsx");
  assert.match(page, /index: false/);
  assert.match(page, /referrer: "no-referrer"/);
  const view = read("components/library/LibraryMaterialView.tsx");
  assert.match(view, /SafeMarkdown/);
  assert.doesNotMatch(view, /dangerouslySetInnerHTML/);
});
