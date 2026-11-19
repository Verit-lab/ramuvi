import { expect, request, test, type APIResponse, type Page } from "./fixtures";

// v0.12 rebranding: "/" is the public Ramuvi landing page, "/learn" the
// student environment and "/teach" the teacher environment. Legacy teacher
// links to "/" keep working through a temporary redirect to "/teach".

const apiBaseUrl = process.env.E2E_API_URL ?? "http://127.0.0.1:8080";
const browserApiBaseUrl = process.env.E2E_BROWSER_API_URL ?? "http://localhost:8080";
const teacherUsername = process.env.E2E_TEACHER_USERNAME ?? "professor";
const teacherPassword = process.env.E2E_TEACHER_PASSWORD ?? "arena-dev-change-me";

test.use({ serviceWorkers: "block" });

async function expectOk(response: APIResponse, operation: string) {
  if (!response.ok()) throw new Error(`${operation} falhou (${response.status()}): ${await response.text()}`);
}

async function loginTeacher(page: Page) {
  const csrf = await page.request.get(`${browserApiBaseUrl}/api/auth/csrf`);
  await expectOk(csrf, "Token CSRF navegador");
  const { token } = (await csrf.json()) as { token: string };
  await expectOk(await page.request.post(`${browserApiBaseUrl}/api/auth/login`, {
    headers: { "X-XSRF-TOKEN": token },
    data: { username: teacherUsername, password: teacherPassword },
  }), "Login navegador");
}

test("v0.12 / Ramuvi: landing loads anonymously with links to Learn, Teach and live join", async ({ page }) => {
  const apiCalls: string[] = [];
  page.on("request", (req) => { if (new URL(req.url()).pathname.startsWith("/api/")) apiCalls.push(req.url()); });

  const response = await page.goto("/");
  expect(response?.status()).toBe(200);
  await expect(page).toHaveURL(/\/$/);
  await expect(page).toHaveTitle(/Ramuvi/);
  await expect(page.getByRole("heading", { level: 1, name: "Ensinar e aprender, em uma nova experiência." })).toBeVisible();
  await expect(page.locator('meta[property="og:site_name"]')).toHaveAttribute("content", "Ramuvi");
  await expect(page.getByText(/Arena Dev/i)).toHaveCount(0);

  const main = page.getByRole("main");
  await expect(main.getByRole("link", { name: "Sou aluno" })).toHaveAttribute("href", "/learn");
  await expect(main.getByRole("link", { name: "Sou professor" })).toHaveAttribute("href", "/teach");
  await expect(page.getByRole("link", { name: "Acessar Learn" })).toHaveAttribute("href", "/learn");
  await expect(page.getByRole("link", { name: "Acessar Teach" })).toHaveAttribute("href", "/teach");
  await expect(page.getByRole("contentinfo").getByRole("link", { name: "Termos de Uso" })).toHaveAttribute("href", "/termos");
  await expect(page.getByRole("contentinfo").getByRole("link", { name: "Política de Privacidade" })).toHaveAttribute("href", "/privacidade");
  await expect(page.getByRole("heading", { name: "Ramuvi Learn" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Ramuvi Teach" })).toBeVisible();
  // No login form on the landing page and no session lookup.
  await expect(page.getByLabel("Senha")).toHaveCount(0);
  expect(apiCalls).toEqual([]);

  // Every internal link resolves (no broken links).
  const hrefs = await page.locator("a[href]").evaluateAll((links) =>
    [...new Set(links.map((link) => link.getAttribute("href") ?? ""))]);
  for (const href of hrefs.filter((value) => value.startsWith("/"))) {
    const linked = await page.request.get(href, { maxRedirects: 0 });
    expect(linked.status(), href).toBeLessThan(400);
  }
  expect(hrefs.filter((value) => value.startsWith("#"))).toContain("#ambientes");
  await expect(page.locator("#ambientes")).toHaveCount(1);
});

test("v0.12 / Ramuvi: landing is usable at 320px and by keyboard", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 640 });
  await page.goto("/");
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await expect(page.getByRole("navigation", { name: "Navegação principal" })).toBeVisible();

  await page.keyboard.press("Tab");
  const skip = page.getByRole("link", { name: "Pular para o conteúdo" });
  await expect(skip).toBeFocused();
  await expect(skip).toBeInViewport();
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "Ramuvi, página inicial" })).toBeFocused();
  const outline = await page.evaluate(() => getComputedStyle(document.activeElement!).outlineStyle);
  expect(outline).not.toBe("none");
});

test("v0.12 / Ramuvi: anonymous teacher at /teach gets the Ramuvi Teach login", async ({ page }) => {
  await page.goto("/teach");
  await expect(page).toHaveURL(/\/teach$/);
  await expect(page).toHaveTitle("Ramuvi Teach");
  await expect(page.getByText("RAMUVI TEACH")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Acesso do professor" })).toBeVisible();
  await expect(page.getByLabel("Senha")).toBeVisible();
  await expect(page.getByRole("link", { name: "Sou aluno: acessar Ramuvi Learn" })).toHaveAttribute("href", "/learn");
});

test("v0.12 / Ramuvi: legacy teacher links at / keep their screen on /teach", async ({ page }) => {
  // This scenario validates routing, not the backend. No API is started by the
  // static frontend test server, so provide the expected anonymous session.
  await page.route("**/api/auth/session", (route) => route.fulfill({ status: 401 }));
  await page.goto("/?tela=biblioteca");
  await expect(page).toHaveURL(/\/teach\?tela=biblioteca$/);
  await page.goto("/?microsoft=connected");
  await expect(page).toHaveURL(/\/teach\?microsoft=connected$/);
  // Unknown parameters never turn the landing page into a redirect.
  await page.goto("/?next=https://example.invalid");
  await expect(page).toHaveURL(/\/\?next=/);
  await expect(page.getByRole("heading", { level: 1, name: "Ensinar e aprender, em uma nova experiência." })).toBeVisible();
});

test("v0.12 / Ramuvi: a student session does not open Teach; teacher APIs stay closed", async ({ page }) => {
  // A verified student account in the browser...
  await page.route("**/api/student-account/session", (route) => route.fulfill({
    status: 200, contentType: "application/json",
    body: JSON.stringify({ accountId: "11111111-1111-4111-8111-111111111111",
      studentId: "22222222-2222-4222-8222-222222222222", role: "STUDENT" }),
  }));
  await page.goto("/teach");
  // ...still meets the teacher login: the route name is not an authorization.
  await expect(page.getByRole("heading", { name: "Acesso do professor" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Suas turmas" })).toHaveCount(0);

  // The backend enforces ROLE_TEACHER regardless of the interface.
  const anonymous = await request.newContext({ baseURL: apiBaseUrl });
  try {
    expect((await anonymous.get("/api/auth/session")).status()).toBe(401);
    expect((await anonymous.get("/api/classrooms")).status()).toBe(401);
  } finally {
    await anonymous.dispose();
  }
});

test("v0.12 / Ramuvi: authenticated teacher works at /teach, survives reload and logs out in place", async ({ page }) => {
  await loginTeacher(page);
  await page.goto("/teach");
  await expect(page.getByRole("heading", { name: "Suas turmas" })).toBeVisible({ timeout: 10_000 });
  await expect(page.locator(".topbar-brand")).toContainText("RAMUVI");

  await page.getByRole("navigation", { name: "Navegação global" }).getByRole("button", { name: "Biblioteca" }).click();
  await expect(page).toHaveURL(/\/teach\?tela=biblioteca$/);
  await page.reload();
  await expect(page.getByRole("heading", { name: "Seus materiais" })).toBeVisible({ timeout: 10_000 });
  await expect(page).toHaveURL(/\/teach\?tela=biblioteca$/);

  // The landing page does not log the teacher out and links back to Teach.
  await page.goto("/");
  await page.getByRole("main").getByRole("link", { name: "Sou professor" }).click();
  await expect(page).toHaveURL(/\/teach$/);
  await expect(page.getByRole("heading", { name: "Suas turmas" })).toBeVisible({ timeout: 10_000 });

  await page.getByRole("button", { name: "Abrir menu do professor" }).click();
  await page.getByRole("menuitem", { name: /Sair/ }).click();
  await expect(page.getByRole("heading", { name: "Acesso do professor" })).toBeVisible();
  await expect(page).toHaveURL(/\/teach/);
  // The server session is gone too, not only the screen.
  await page.reload();
  await expect(page.getByRole("heading", { name: "Acesso do professor" })).toBeVisible();
});

test("v0.12 / Ramuvi: anonymous learner at /learn sees the Ramuvi Learn entry", async ({ page }) => {
  await page.route("**/api/student-account/session", (route) => route.fulfill({ status: 401 }));
  await page.route("**/api/student-account/providers", (route) => route.fulfill({
    status: 200, contentType: "application/json", body: "[]",
  }));
  await page.goto("/learn");
  await expect(page).toHaveTitle("Ramuvi Learn");
  await expect(page.getByText("Ramuvi Learn · Área do aluno")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Acesse sua conta" })).toBeVisible();
});
