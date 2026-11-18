import assert from "node:assert/strict";
import test from "node:test";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, resolve } from "node:path";
import { BRAND, ENTRY_ROUTES } from "../lib/brand";
import { LEGACY_TEACHER_QUERY_PARAMS, legacyTeacherRedirect } from "../lib/legacy-routes";

const frontend = process.cwd();
const root = resolve(frontend, "..");
const read = (path: string) => readFileSync(resolve(frontend, path), "utf8");

test("v0.12 entry points: landing, Learn and Teach are fixed same-origin paths", () => {
  assert.deepEqual(ENTRY_ROUTES, { landing: "/", learn: "/learn", teach: "/teach", liveJoin: "/join" });
  assert.equal(BRAND.name, "Ramuvi");
  assert.equal(BRAND.learn, "Ramuvi Learn");
  assert.equal(BRAND.teach, "Ramuvi Teach");
});

test("plain visits to / stay on the public landing page", () => {
  assert.equal(legacyTeacherRedirect("/", ""), null);
  assert.equal(legacyTeacherRedirect("/", "?utm_source=folder"), null);
  assert.equal(legacyTeacherRedirect("/", "?next=https://evil.example"), null);
});

test("legacy teacher deep links at / are sent to /teach with the same query", () => {
  assert.equal(legacyTeacherRedirect("/", "?tela=biblioteca"), "/teach?tela=biblioteca");
  assert.equal(
    legacyTeacherRedirect("/", "?tela=turma&turma=abc&aba=jornadas&jornada=nova"),
    "/teach?tela=turma&turma=abc&aba=jornadas&jornada=nova",
  );
  assert.equal(legacyTeacherRedirect("/", "?journey=j-1"), "/teach?journey=j-1");
  for (const provider of ["google", "microsoft", "notion"]) {
    assert.equal(legacyTeacherRedirect("/", `?${provider}=connected`), `/teach?${provider}=connected`);
  }
  assert.ok(LEGACY_TEACHER_QUERY_PARAMS.includes("tela"));
});

test("legacy redirect never lets a parameter choose the destination", () => {
  // Whatever else is in the query, the path is always /teach.
  const target = legacyTeacherRedirect("/", "?tela=turma&redirect=https://evil.example&next=//evil.example");
  assert.ok(target?.startsWith("/teach?"));
  assert.equal(new URL(target!, "https://ramuvi.io").origin, "https://ramuvi.io");
  assert.equal(new URL(target!, "https://ramuvi.io").pathname, "/teach");
  // Only "/" is a legacy teacher entry; other routes keep their own queries.
  assert.equal(legacyTeacherRedirect("/learn", "?tela=turma"), null);
  assert.equal(legacyTeacherRedirect("/teach", "?tela=turma"), null);
  assert.equal(legacyTeacherRedirect("/join", "?journey=x"), null);
});

test("proxy applies the legacy redirect as temporary and keeps the CSP header on it", () => {
  const proxy = read("proxy.ts");
  assert.match(proxy, /legacyTeacherRedirect\(request\.nextUrl\.pathname, request\.nextUrl\.search\)/);
  assert.match(proxy, /NextResponse\.redirect\(target, 307\)/);
  assert.match(proxy, /request\.nextUrl\.clone\(\)/);
  assert.match(proxy, /Content-Security-Policy/);
});

test("landing page is static, links both environments and carries Ramuvi metadata", () => {
  const landing = read("app/page.tsx");
  assert.doesNotMatch(landing, /"use client"/);
  assert.doesNotMatch(landing, /ArenaApp|TeacherLogin|fetch\(/);
  assert.match(landing, /href=\{ENTRY_ROUTES\.learn\}/);
  assert.match(landing, /href=\{ENTRY_ROUTES\.teach\}/);
  assert.match(landing, />Sou aluno</);
  assert.match(landing, />Sou professor</);
  assert.match(landing, />Acessar Learn</);
  assert.match(landing, />Acessar Teach</);
  assert.match(landing, /openGraph/);
  assert.match(landing, /<main id="conteudo"/);
  assert.match(landing, /aria-label="Navegação principal"/);
  // No invented social proof or institutional claims.
  assert.doesNotMatch(landing, /depoimento|parceir|certifica|milhares|usuários ativos/i);
});

test("Teach renders the existing teacher app; Learn keeps its shell and gets its own title", () => {
  const teach = read("app/teach/page.tsx");
  assert.match(teach, /import ArenaApp from "@\/components\/ArenaApp"/);
  assert.match(teach, /BRAND\.teach/);
  const learnLayout = read("app/learn/layout.tsx");
  assert.doesNotMatch(learnLayout, /"use client"/);
  assert.match(learnLayout, /BRAND\.learn/);
  assert.match(learnLayout, /LearnLayoutShell/);
});

test("teacher preview links return to /teach instead of the landing page", () => {
  const journey = read("app/learn/journeys/[journeyId]/page.tsx");
  const content = read("app/learn/journeys/[journeyId]/content/[contentId]/page.tsx");
  for (const source of [journey, content]) {
    assert.match(source, /\/teach\?journey=\$\{encodeURIComponent\(journeyId\)\}/);
    assert.doesNotMatch(source, /href=\{`\/\?journey=/);
  }
  assert.match(journey, /<Link href="\/teach" className=\{styles\.studyBack\}>/);
});

test("root metadata, manifest and icons use the Ramuvi identity", () => {
  const layout = read("app/layout.tsx");
  const manifest = read("app/manifest.ts");
  assert.match(layout, /applicationName: BRAND\.name/);
  assert.match(layout, /template: `%s · \$\{BRAND\.name\}`/);
  assert.match(layout, /\/icons\/ramuvi\.svg/);
  assert.match(manifest, /name: BRAND\.name/);
  assert.match(manifest, /\/icons\/ramuvi-maskable-512\.png/);
  for (const icon of ["ramuvi.svg", "ramuvi-32.png", "ramuvi-192.png", "ramuvi-512.png", "ramuvi-apple-180.png", "ramuvi-maskable-512.png"]) {
    assert.ok(statSync(resolve(frontend, "public/icons", icon)).size > 0, icon);
  }
  assert.match(read("public/offline.html"), /<title>Ramuvi · Sem conexão<\/title>/);
});

test("backend OAuth integrations return teachers to /teach", () => {
  const providers = resolve(root, "backend/src/main/java/br/com/arenadev/integration/provider");
  for (const [dir, file, provider] of [
    ["google", "GoogleDelegatedOAuthService.java", "google"],
    ["microsoft", "MicrosoftDelegatedOAuthService.java", "microsoft"],
    ["notion", "NotionDelegatedOAuthService.java", "notion"],
  ]) {
    const source = readFileSync(join(providers, dir, file), "utf8");
    assert.match(source, new RegExp(`"/teach\\?${provider}=connected"`), file);
  }
});

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(path);
    return /\.(tsx?|css|html|js)$/.test(entry.name) ? [path] : [];
  });
}

test("rebranding audit: no visible Arena Dev identity remains in active interfaces", () => {
  const files = ["app", "components", "lib", "styles", "public"].flatMap((dir) => sourceFiles(resolve(frontend, dir)));
  const offenders = files.filter((file) => /Arena Dev|ARENA DEV|ArenaDev/.test(readFileSync(file, "utf8")));
  assert.deepEqual(offenders, []);
});
