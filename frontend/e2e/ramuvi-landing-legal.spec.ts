import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { expect, test } from "./fixtures";

const axeSource = readFileSync(resolve(process.cwd(), "node_modules/axe-core/axe.min.js"), "utf8");

// v0.12.x audit (2026-11-19): landing redesign, unified legal links and the
// legal documents' publication gate. Frontend-only: API calls are mocked.

test.use({ serviceWorkers: "block" });

const VIEWPORTS = [320, 360, 390, 430, 768, 1024, 1440, 1920];

test("landing: code form opens /join with the typed code, without JavaScript of its own", async ({ page }) => {
  await page.route("**/api/**", (route) => route.fulfill({ status: 404, body: "" }));
  await page.goto("/");
  const form = page.getByRole("form", { name: "Vai entrar em uma aula agora?" });
  await form.getByLabel("Código da aula").fill("abc123");
  await form.getByRole("button", { name: "Entrar na aula" }).click();
  await expect(page).toHaveURL(/\/join\?code=abc123$/);
});

test("landing: no horizontal overflow at the audited widths, both themes", async ({ page }) => {
  for (const scheme of ["light", "dark"] as const) {
    await page.emulateMedia({ colorScheme: scheme, reducedMotion: "reduce" });
    for (const width of VIEWPORTS) {
      await page.setViewportSize({ width, height: 900 });
      // Let link prefetches settle: WebKit reports aborted RSC prefetches as page errors.
      await page.goto("/", { waitUntil: "networkidle" });
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `${scheme} ${width}px`).toBeLessThanOrEqual(0);
    }
  }
});

test("landing with FAQ open: axe WCAG 2.2 AA has no serious or critical findings in light and dark", async ({ page }) => {
  for (const scheme of ["light", "dark"] as const) {
    await page.emulateMedia({ colorScheme: scheme });
    await page.goto("/");
    // FAQ answers are part of the audit too.
    for (const summary of await page.locator("details summary").all()) await summary.click();
    await page.addScriptTag({ content: axeSource });
    const results = await page.evaluate(() => (window as unknown as { axe: { run: (context: string, options: object) =>
      Promise<{ violations: Array<{ id: string; impact: string; nodes: Array<{ target: string[] }> }> }> } }).axe
      .run("body", { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"] } }));
    const blocking = results.violations.filter((issue) => issue.impact === "serious" || issue.impact === "critical");
    expect(blocking.map((issue) => `${issue.id}: ${issue.nodes.map((node) => node.target).join(", ")}`), scheme).toEqual([]);
  }
});

test("legal pages: Verit identification, explicit pending items, noindex and no invented data", async ({ page }) => {
  for (const [path, title] of [["/termos", "Termos de Uso"], ["/privacidade", "Política de Privacidade"]] as const) {
    await page.goto(path);
    await expect(page.getByRole("heading", { level: 1, name: title })).toBeVisible();
    await expect(page.getByText(/Verit Soluções Digitais LTDA/).first()).toBeVisible();
    await expect(page.getByText(/Verite/i)).toHaveCount(0);
    await expect(page.getByText(/Arena Dev/i)).toHaveCount(0);
    // No CNPJ and no effective date while the legal gate is open.
    expect(await page.locator("main").innerText()).not.toMatch(/\d{2}\.\d{3}\.\d{3}\/\d{4}-\d{2}/);
    await expect(page.getByText(/Ainda sem data de vigência/)).toBeVisible();
    await expect(page.getByRole("heading", { name: "Minuta em revisão" })).toBeVisible();
    await expect(page.getByText(/CNPJ e endereço da Verit Soluções Digitais LTDA/)).toBeVisible();
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
    // Table of contents points at real sections.
    for (const href of await page.getByRole("navigation", { name: "Nesta página" }).locator("a").evaluateAll((links) => links.map((link) => link.getAttribute("href")))) {
      await expect(page.locator(href!), href!).toHaveCount(1);
    }
    await page.setViewportSize({ width: 320, height: 700 });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow, path).toBeLessThanOrEqual(0);
    await page.setViewportSize({ width: 1280, height: 800 });
  }
});

test("learner login: back link, account-first layout and a single legal line", async ({ page }) => {
  await page.route("**/api/student-account/session", (route) => route.fulfill({ status: 401 }));
  await page.route("**/api/student-account/providers", (route) => route.fulfill({
    status: 200, contentType: "application/json",
    body: JSON.stringify([{ id: "google", name: "Google", path: "/api/student-oidc/authorization/google" }]),
  }));
  await page.setViewportSize({ width: 320, height: 700 });
  await page.goto("/learn");
  await expect(page.getByRole("heading", { name: "Acesse sua conta" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Continuar com Google" })).toBeVisible();
  await expect(page.getByRole("link", { name: /Voltar para a Ramuvi/ })).toHaveAttribute("href", "/");
  const legal = page.getByRole("navigation", { name: "Documentos legais" });
  await expect(legal.getByRole("link", { name: "Termos de Uso" })).toHaveAttribute("href", "/termos");
  await expect(legal.getByRole("link", { name: "Política de Privacidade" })).toHaveAttribute("href", "/privacidade");
  await expect(page.getByRole("main").getByRole("link", { name: "Termos de Uso" })).toHaveCount(1);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
});
