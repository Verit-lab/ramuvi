import { expect, test } from "./fixtures";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

// Mocked API tests must not let a previously installed PWA worker bypass routes.
test.use({ serviceWorkers: "block" });

const axeSource = readFileSync(resolve(process.cwd(), "node_modules/axe-core/axe.min.js"),"utf8");

type AxeFinding = { id: string; impact: string; nodes: Array<{ target: string[]; failureSummary: string }> };

async function audit(page: import("@playwright/test").Page, selector: string) {
  await page.addScriptTag({ content: axeSource });
  const results = await page.evaluate(async (target) => {
    const engine = (window as unknown as { axe: { run: (selector: string, options: object) =>
      Promise<{ violations: AxeFinding[] }> } }).axe;
    return engine.run(target, { runOnly: { type: "tag", values: ["wcag2a","wcag2aa","wcag21a","wcag21aa","wcag22aa"] } });
  },selector);
  const findings = results.violations.map((item)=>({
    id:item.id, impact:item.impact, targets:item.nodes.slice(0,3).map((n)=>n.target.join(" ")),
  }));
  console.log(`WCAG AUTOMATED ${selector}: ${JSON.stringify(findings)}`);
  return results.violations;
}

test("v0.12 / Ramuvi: landing pública sem achados sérios ou críticos no axe-core WCAG A/AA", async ({ page }) => {
  const baseURL = process.env.E2E_FRONTEND_URL ?? "http://localhost:3000";
  for (const scheme of ["dark", "light"] as const) {
    await page.emulateMedia({ colorScheme: scheme });
    await page.goto(baseURL);
    const findings = await audit(page, "body");
    expect(findings.filter((issue) => issue.impact === "critical" || issue.impact === "serious"), scheme).toEqual([]);
  }
});

test("Ramuvi Learn: conta autenticada mantém contraste WCAG AA nos temas escuro e claro", async ({ page }) => {
  await page.route("**/api/student-account/session", (route) => route.fulfill({
    status: 200, contentType: "application/json",
    body: JSON.stringify({
      accountId: "11111111-1111-4111-8111-111111111111",
      studentId: null,
      role: "STUDENT",
    }),
  }));
  await page.route("**/api/student-account/classrooms", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }));
  await page.route("**/api/student-account/providers", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }));

  for (const scheme of ["dark", "light"] as const) {
    await page.emulateMedia({ colorScheme: scheme });
    await page.goto("/learn?account=1");
    await expect(page.getByRole("heading", { name: "Minha conta" })).toBeVisible();
    await expect(page.getByText("Nenhuma turma ativa foi encontrada para esta conta.")).toBeVisible();
    const findings = await audit(page, "body");
    expect(findings.filter((issue) => issue.impact === "critical" || issue.impact === "serious"), scheme).toEqual([]);
  }
});

test("documentos legais são acessíveis sem login e possuem navegação e aviso de minuta", async ({ page }) => {
  for (const [path, title] of [["/termos", "Termos de Uso"], ["/privacidade", "Política de Privacidade"]] as const) {
    const response = await page.goto(path);
    expect(response?.status()).toBe(200);
    await expect(page.getByRole("heading", { name: title, level: 1 })).toBeVisible();
    await expect(page.getByText(/Minuta em revisão/)).toBeVisible();
    await expect(page.getByRole("link", { name: "Início" })).toHaveAttribute("href", "/");
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
    const findings = await audit(page, "body");
    expect(findings.filter((issue) => issue.impact === "critical" || issue.impact === "serious"), path).toEqual([]);
  }
});

test("login não apresenta achados críticos no axe-core WCAG A/AA", async ({ page }) => {
  // This is a visual-only audit; a live teacher backend is not required.
  await page.route("**/api/auth/session", (route) => route.fulfill({ status: 401 }));
  const baseURL = process.env.E2E_FRONTEND_URL ?? "http://localhost:3000";
  await page.goto(`${baseURL}/teach`);
  await page.getByLabel("Usuário").fill(process.env.E2E_TEACHER_USERNAME ?? "professor");
  await page.getByLabel("Senha").fill(process.env.E2E_TEACHER_PASSWORD ?? "arena-dev-change-me");
  await expect(page.getByRole("button", { name: "Entrar", exact: true })).toBeVisible();
  const loginFindings=await audit(page, "body");
  expect(loginFindings.filter((issue)=>issue.impact==="critical")).toEqual([]);
});
