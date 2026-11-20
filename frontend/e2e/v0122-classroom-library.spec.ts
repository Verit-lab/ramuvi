import { expect, request, test, type APIRequestContext, type APIResponse, type Page } from "./fixtures";

/**
 * v0.12.2 (ADR-0076) against a REAL backend with APP_CLASSROOM_HUB_ENABLED and
 * APP_LIBRARY_SHARING_ENABLED ON (other switches OFF). Student OIDC flows cannot use a real
 * Google/Microsoft provider locally, so the member-join and SAVE flows mock only the
 * student-account endpoints; teacher hub, sharing and the anonymous VIEW preview are real.
 */

const apiBaseUrl = process.env.E2E_API_URL ?? "http://127.0.0.1:8080";
const browserApiBaseUrl = process.env.E2E_BROWSER_API_URL ?? "http://localhost:8080";
const teacherUsername = process.env.E2E_TEACHER_USERNAME ?? "professor";
const teacherPassword = process.env.E2E_TEACHER_PASSWORD ?? "arena-dev-change-me";

async function expectOk(response: APIResponse, operation: string) {
  if (!response.ok()) throw new Error(`${operation} falhou (${response.status()}): ${await response.text()}`);
}

async function teacherApi(): Promise<{ api: APIRequestContext; headers: Record<string, string> }> {
  const api = await request.newContext({ baseURL: apiBaseUrl });
  const csrf = await api.get("/api/auth/csrf");
  await expectOk(csrf, "CSRF");
  const { token } = (await csrf.json()) as { token: string };
  const headers = { "X-XSRF-TOKEN": token };
  await expectOk(await api.post("/api/auth/login", { headers, data: { username: teacherUsername, password: teacherPassword } }), "Login API");
  return { api, headers };
}

async function createClassroom(name: string) {
  const { api, headers } = await teacherApi();
  const classroom = await api.post("/api/classrooms", { headers, data: { name, code: `V122-${Date.now().toString(36).slice(-6)}` } });
  await expectOk(classroom, "Turma");
  const { id } = (await classroom.json()) as { id: string };
  const student = await api.post("/api/students", { headers, data: { registration: `R${Date.now()}`, name: "Aluna Hub" } });
  await expectOk(student, "Aluno");
  const { id: studentId } = (await student.json()) as { id: string };
  await expectOk(await api.post(`/api/classrooms/${id}/students/${studentId}`, { headers }), "Matrícula");
  await api.dispose();
  return id;
}

async function createLibraryResource(title: string, body: string) {
  const { api, headers } = await teacherApi();
  const created = await api.post("/api/library/resources", {
    headers,
    data: { title, summary: "Resumo do material", body, format: "MARKDOWN", sourceType: "MANUAL", tags: [] },
  });
  await expectOk(created, "Recurso");
  const { id } = (await created.json()) as { id: string };
  await api.dispose();
  return id;
}

async function login(page: Page) {
  const csrf = await page.request.get(`${browserApiBaseUrl}/api/auth/csrf`);
  await expectOk(csrf, "CSRF navegador");
  const { token } = (await csrf.json()) as { token: string };
  await expectOk(await page.request.post(`${browserApiBaseUrl}/api/auth/login`, {
    headers: { "X-XSRF-TOKEN": token },
    data: { username: teacherUsername, password: teacherPassword },
  }), "Login navegador");
}

async function expectNoHorizontalOverflow(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow, "sem rolagem horizontal").toBeLessThanOrEqual(1);
}

test.describe("I1 — central da turma", () => {
  test("inicia em uma ação, retoma, troca com confirmação e preserva o histórico", async ({ page }) => {
    const name = `Turma central ${Date.now().toString(36)}`;
    const classroomId = await createClassroom(name);
    await login(page);
    await page.goto(`/teach?tela=turma&turma=${classroomId}`);

    const hub = page.getByTestId("classroom-hub");
    await expect(hub.getByTestId("classroom-hub-primary")).toHaveAttribute("data-state", "start");
    await page.getByTestId("hub-session-title").fill("Aula 1 E2E");
    await page.getByTestId("hub-start").click();
    await expect(page.getByRole("button", { name: `Voltar para a turma ${name}` })).toBeVisible({ timeout: 15_000 });

    await page.getByRole("button", { name: `Voltar para a turma ${name}` }).click();
    await expect(hub.getByTestId("classroom-hub-primary")).toHaveAttribute("data-state", "resume");
    await expect(hub).toContainText("Aula 1 E2E");
    await expect(hub).toContainText("0 de 1 presente(s)");

    // Keyboard: the confirmation dialog opens, traps focus and closes with Escape without effect.
    await page.getByTestId("hub-switch").focus();
    await page.keyboard.press("Enter");
    const dialog = page.getByTestId("hub-switch-dialog");
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText("presença");
    for (let i = 0; i < 6; i += 1) await page.keyboard.press("Tab");
    expect(await dialog.evaluate((node) => node.contains(document.activeElement))).toBe(true);
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
    await expect(hub.getByTestId("classroom-hub-primary")).toHaveAttribute("data-state", "resume");

    await page.getByTestId("hub-switch").click();
    await dialog.getByRole("textbox").fill("Aula 2 E2E");
    await page.getByTestId("hub-switch-confirm").click();
    await expect(page.getByRole("button", { name: `Voltar para a turma ${name}` })).toBeVisible({ timeout: 15_000 });
    await page.getByRole("button", { name: `Voltar para a turma ${name}` }).click();
    await expect(hub).toContainText("Aula 2 E2E");
    await expect(page.getByTestId("hub-history")).toContainText("Aula 1 E2E");
  });

  test("conflito de aula ativa em outra aba vira 409 tipado e a central se atualiza", async ({ page }) => {
    const classroomId = await createClassroom(`Turma conflito ${Date.now().toString(36)}`);
    await login(page);
    await page.goto(`/teach?tela=turma&turma=${classroomId}`);
    await expect(page.getByTestId("classroom-hub-primary")).toHaveAttribute("data-state", "start");
    // Another tab starts the class meanwhile.
    const { api, headers } = await teacherApi();
    await expectOk(await api.post(`/api/classrooms/${classroomId}/sessions`, { headers, data: { title: "Aberta em outra aba" } }), "Outra aba");
    await api.dispose();
    await page.getByTestId("hub-start").click();
    await expect(page.getByTestId("classroom-hub-primary")).toHaveAttribute("data-state", "resume");
    await expect(page.getByTestId("classroom-hub")).toContainText("Aberta em outra aba");
  });

  test("central responsiva em 320px sem rolagem horizontal", async ({ page }) => {
    const classroomId = await createClassroom(`Turma estreita com nome bem comprido ${Date.now().toString(36)}`);
    await page.setViewportSize({ width: 320, height: 720 });
    await login(page);
    await page.goto(`/teach?tela=turma&turma=${classroomId}`);
    await expect(page.getByTestId("hub-start")).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });
});

test.describe("I3a — compartilhar e prévia pública", () => {
  test("professor compartilha sem turma; visitante abre VIEW sem conta; revogação fecha o link", async ({ page, browser }) => {
    const title = `Guia compartilhado ${Date.now().toString(36)}`;
    await createLibraryResource(title, "# Introdução\n\nConteúdo **seguro** do guia.\n\n<script>window.__xss = 1</script>");
    await login(page);
    await page.goto("/teach?tela=biblioteca");
    await page.getByRole("button", { name: new RegExp(title) }).first().click();
    await expect(page.getByRole("button", { name: "Adicionar a uma Jornada" })).toBeVisible();
    await page.getByTestId("library-share-open").click();
    const dialog = page.getByTestId("library-share-dialog");
    await expect(dialog).toContainText("Não adiciona à Jornada nem apresenta na aula");
    await dialog.getByLabel(/Somente visualizar/).check();
    await page.getByTestId("library-share-create").click();
    const link = await page.getByTestId("library-share-link").inputValue();
    expect(link).toMatch(/\/learn\/library\/shared#acesso=[A-Z2-7]{32}$/);
    await expect(page.getByTestId("library-share-list")).toContainText("Ativo");
    await expect(page.getByTestId("library-share-list")).not.toContainText(link.split("#acesso=")[1]);

    const visitor = await browser.newContext();
    const reader = await visitor.newPage();
    const requests: string[] = [];
    reader.on("request", (req) => requests.push(req.url()));
    await reader.goto(link);
    await expect(reader.getByTestId("library-material")).toContainText("Conteúdo");
    await expect(reader.getByRole("heading", { name: title })).toBeVisible();
    expect(reader.url()).not.toContain("#acesso=");
    expect(requests.some((url) => url.includes(link.split("#acesso=")[1]))).toBe(false);
    expect(await reader.evaluate(() => (window as unknown as { __xss?: number }).__xss)).toBeUndefined();
    await expect(reader.getByRole("navigation", { name: "Navegação de estudos" })).toHaveCount(0);
    await expect(reader.getByText("Somente visualização")).toBeVisible();
    await expect(reader.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);

    await page.getByTestId("library-share-list").getByRole("button", { name: "Revogar" }).first().click({ trial: true });
    page.once("dialog", (confirm) => void confirm.accept());
    await page.getByTestId("library-share-list").getByRole("button", { name: "Revogar" }).first().click();
    await expect(page.getByTestId("library-share-list")).toContainText("Revogado");

    await reader.reload();
    await expect(reader.getByTestId("shared-material-error")).toHaveAttribute("data-kind", "UNAVAILABLE");
    await visitor.close();
  });

  test("diálogo de compartilhar é operável por teclado e cabe em 320px", async ({ page }) => {
    const title = `Material teclado ${Date.now().toString(36)}`;
    await createLibraryResource(title, "Texto do material");
    await page.setViewportSize({ width: 320, height: 720 });
    await login(page);
    await page.goto("/teach?tela=biblioteca");
    await page.getByRole("button", { name: new RegExp(title) }).first().click();
    await page.getByTestId("library-share-open").focus();
    await page.keyboard.press("Enter");
    const dialog = page.getByTestId("library-share-dialog");
    await expect(dialog).toBeVisible();
    await expectNoHorizontalOverflow(page);
    for (let i = 0; i < 8; i += 1) await page.keyboard.press("Tab");
    expect(await dialog.evaluate((node) => node.contains(document.activeElement))).toBe(true);
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
  });
});

test.describe("I2/I3b — aluno com conta (endpoints de conta simulados)", () => {
  // Mocks only see page requests; the PWA service worker would fetch around them (as in
  // verified-journey-i3.spec.ts).
  test.use({ serviceWorkers: "block" });
  const session = { accountId: "44444444-4444-4444-8444-444444444444", studentId: null, role: "STUDENT" };

  async function mockStudent(page: Page, capabilities: { liveMemberJoin: boolean; personalLibrary: boolean }) {
    await page.route("**/api/student-account/session", (route) => route.fulfill({ json: session }));
    await page.route("**/api/student-account/capabilities", (route) => route.fulfill({ json: capabilities }));
  }

  test("Minha Biblioteca sem matrícula: vazia, salvar por link SAVE, ler e remover", async ({ page }) => {
    await mockStudent(page, { liveMemberJoin: false, personalLibrary: true });
    const items: Array<Record<string, unknown>> = [];
    const token = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    await page.route("**/api/library/shares/preview", async (route) => {
      expect(route.request().postDataJSON()).toEqual({ token });
      await route.fulfill({ json: {
        title: "Ficha de estudo", summary: null, format: "PLAIN_TEXT", body: "Linha 1\nLinha 2",
        sourceUrl: null, sourceLabel: null, revision: 3, copyProtected: false, externalReference: false,
        permission: "SAVE", expiresAt: "2026-12-30T00:00:00Z", canSave: true,
        alreadySaved: items.length > 0, savedVersionDiffers: false,
      } });
    });
    await page.route("**/api/student-account/library/items", async (route) => {
      if (route.request().method() === "POST") {
        expect(route.request().headers()["x-xsrf-token"]).toBeTruthy();
        items.push({ id: "item-1", title: "Ficha de estudo", summary: null, format: "PLAIN_TEXT", revision: 3,
          externalReference: false, savedAt: "2026-11-20T12:00:00Z", updatedAt: "2026-11-20T12:00:00Z" });
        await route.fulfill({ status: 201, json: { itemId: "item-1", status: "SAVED", newerVersionAvailable: false } });
        return;
      }
      await route.fulfill({ json: items });
    });
    await page.route("**/api/student-account/library/items/item-1", async (route) => {
      if (route.request().method() === "DELETE") {
        items.splice(0, items.length);
        await route.fulfill({ status: 204, body: "" });
        return;
      }
      await route.fulfill({ json: { id: "item-1", title: "Ficha de estudo", summary: null, format: "PLAIN_TEXT",
        body: "Linha 1\nLinha 2", sourceUrl: null, sourceLabel: null, revision: 3, copyProtected: false,
        externalReference: false, savedAt: "2026-11-20T12:00:00Z" } });
    });
    await page.route("**/api/auth/csrf", (route) => route.fulfill({ json: { token: "csrf-e2e" } }));

    await page.goto("/learn/library");
    await expect(page.getByTestId("my-library-empty")).toBeVisible();
    await expectNoHorizontalOverflow(page);

    await page.goto(`/learn/library/shared#acesso=${token}`);
    await expect(page.getByTestId("library-material")).toContainText("Linha 2");
    expect(page.url()).not.toContain("#acesso=");
    await page.getByTestId("shared-material-save").click();
    await expect(page.getByTestId("shared-material-saved")).toContainText("Material salvo");

    await page.goto("/learn/library");
    await expect(page.getByTestId("my-library-list")).toContainText("Ficha de estudo");
    await page.getByRole("link", { name: "Ficha de estudo" }).click();
    await expect(page.getByTestId("library-material")).toContainText("Linha 1");
    await page.getByRole("link", { name: "← Minha Biblioteca" }).click();
    page.once("dialog", (confirm) => void confirm.accept());
    await page.getByRole("button", { name: "Remover Ficha de estudo da Minha Biblioteca" }).click();
    await expect(page.getByTestId("my-library-empty")).toBeVisible();
  });

  test("link SAVE sem conta pede login e não mostra o conteúdo", async ({ page }) => {
    await page.route("**/api/library/shares/preview", (route) => route.fulfill({
      status: 401, json: { status: 401, code: "ACCOUNT_REQUIRED", message: "Entre com sua conta de estudante para abrir e salvar este material." },
    }));
    await page.goto("/learn/library/shared#acesso=ABCDEFGHIJKLMNOPQRSTUVWXYZ234567");
    await expect(page.getByTestId("shared-material-error")).toHaveAttribute("data-kind", "ACCOUNT_REQUIRED");
    await expect(page.getByRole("link", { name: "Entrar com conta de estudante" })).toHaveAttribute("href", "/learn?account=1");
    await expect(page.getByTestId("library-material")).toHaveCount(0);
  });

  test("membro vê a aula ao vivo e entra sem digitar código", async ({ page }) => {
    await page.route("**/api/student-account/session", (route) => route.fulfill({ json: { ...session, studentId: "55555555-5555-4555-8555-555555555555" } }));
    await page.route("**/api/student-account/capabilities", (route) => route.fulfill({ json: { liveMemberJoin: true, personalLibrary: true } }));
    await page.route("**/api/student-account/study/journeys", (route) => route.fulfill({ json: [] }));
    await page.route("**/api/student-account/live-sessions", (route) => route.fulfill({ json: [{
      sessionId: "66666666-6666-4666-8666-666666666666", classroomId: "77777777-7777-4777-8777-777777777777",
      classroomName: "Turma do membro", sessionTitle: "Aula de hoje", startedAt: "2026-11-20T12:00:00Z",
    }] }));
    let joinBody: string | null = null;
    await page.route("**/api/student-account/live-sessions/*/join", async (route) => {
      joinBody = route.request().postData();
      await route.fulfill({ json: {
        token: "participant-token", code: "ABC234", sessionId: "66666666-6666-4666-8666-666666666666",
        classroomName: "Turma do membro", sessionTitle: "Aula de hoje", participantId: "p1",
        studentId: "55555555-5555-4555-8555-555555555555", name: "Aluna", displayName: "Aluna",
        present: true, expiresAt: "2099-01-01T00:00:00Z", profileComplete: true,
      } });
    });
    await page.route("**/api/join/ABC234**", (route) => route.fulfill({ status: 404, json: { message: "fim do teste" } }));
    await page.route("**/api/auth/csrf", (route) => route.fulfill({ json: { token: "csrf-e2e" } }));
    await page.goto("/learn");
    const live = page.getByTestId("member-live");
    await expect(live).toContainText("Turma do membro");
    await expect(live).toContainText("não precisa digitar o código");
    await live.getByTestId("member-live-enter").click();
    await expect(page).toHaveURL(/\/join\?code=ABC234/);
    expect(joinBody).toBe("{}");
    expect(await page.evaluate(() => sessionStorage.getItem("arena-dev-student:ABC234"))).toContain("participant-token");
  });
});
