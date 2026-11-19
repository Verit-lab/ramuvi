import { expect, test, type Page } from "@playwright/test";

/**
 * ADR-0074 student UI with mocked API (backend security is covered by
 * ClassroomJoinLinkHttpIT). The token travels only in the URL fragment and POST bodies.
 */
test.use({ serviceWorkers: "block" });
const token = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
const classroomName = "Turma Desenvolvimento";
const accountId = "11111111-1111-4111-8111-111111111111";
const studentId = "22222222-2222-4222-8222-222222222222";

async function mockAccount(page: Page, state: { authenticated: boolean; linked: boolean }) {
  await page.route("**/api/student-account/providers", route => route.fulfill({
    status: 200, contentType: "application/json",
    body: JSON.stringify([{ id: "google", name: "Google", path: "/api/student-oidc/authorization/google" }]),
  }));
  await page.route("**/api/student-account/session", route => route.fulfill(
    state.authenticated ? { status: 200, contentType: "application/json",
      body: JSON.stringify({ accountId, studentId: state.linked ? studentId : null, role: "STUDENT" }) }
      : { status: 401 },
  ));
  await page.route("**/api/student-account/classrooms", route => route.fulfill({
    status: 200, contentType: "application/json",
    body: JSON.stringify(state.linked ? [{ enrollmentId: "e1", classroomId: "c1",
      classroomName, studentStudyAccessEnabled: false }] : []),
  }));
  await page.route("**/api/student-account/classroom-links/requests", route => route.fulfill({
    status: 200, contentType: "application/json", body: "[]",
  }));
  await page.route("**/api/auth/csrf", route => route.fulfill({
    status: 200, contentType: "application/json", body: '{"token":"csrf-mock"}',
  }));
}

test("link de ingresso sobrevive ao login, pede confirmação e só então ingressa", async ({ page }) => {
  const state = { authenticated: false, linked: false };
  await mockAccount(page, state);
  const requests: string[] = [];
  page.on("request", request => requests.push(request.url()));
  let previews = 0;
  const accepted: unknown[] = [];
  await page.route("**/api/student-account/classroom-links/preview", route => {
    previews++;
    expect(route.request().postDataJSON()).toEqual({ token });
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      classroomName, expiresAt: "2026-12-01T00:00:00Z", requiresApproval: false,
      alreadyMember: false, pendingRequest: false }) });
  });
  await page.route("**/api/student-account/classroom-links/accept", route => {
    accepted.push(route.request().postDataJSON());
    state.linked = true;
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      status: "JOINED_NEW_STUDENT", classroomName, journeysAvailable: false }) });
  });

  await page.goto("/learn?account=1#ingresso=" + token.toLowerCase());
  await expect(page.getByRole("link", { name: "Continuar com Google" })).toBeVisible();
  expect(new URL(page.url()).hash).toBe("");
  expect(await page.evaluate(() => sessionStorage.getItem("ramuvi:pending-join-link"))).toBe(token);

  state.authenticated = true;
  await page.goto("/learn?connected=1"); // Simula retorno do provedor OIDC.
  await expect(page.getByText("Entrar na turma " + classroomName + " com esta conta?")).toBeVisible();
  expect(previews).toBe(1);
  expect(accepted).toHaveLength(0); // Nada é efetivado sem confirmação.

  await page.getByRole("button", { name: "Confirmar e entrar na turma" }).click();
  const status = page.getByRole("status").filter({ hasText: "Você entrou na turma" });
  await expect(status).toContainText("quando o professor liberar o acesso a estudos");
  await expect(status).not.toContainText("pontuações");
  expect(accepted).toEqual([{ token }]);
  expect(await page.evaluate(() => sessionStorage.getItem("ramuvi:pending-join-link"))).toBeNull();
  expect(requests.filter(url => url.includes(token) || url.includes(token.toLowerCase()))).toEqual([]);
  await page.reload();
  expect(accepted).toHaveLength(1);
});

test("falha de rede mantém o link para nova tentativa; pedido pendente é explicado", async ({ page }) => {
  await mockAccount(page, { authenticated: true, linked: false });
  await page.route("**/api/student-account/classroom-links/preview", route => route.fulfill({
    status: 200, contentType: "application/json", body: JSON.stringify({
      classroomName, expiresAt: "2026-12-01T00:00:00Z", requiresApproval: true,
      alreadyMember: false, pendingRequest: false }) }));
  let attempts = 0;
  await page.route("**/api/student-account/classroom-links/accept", route => {
    attempts++;
    if (attempts === 1) return route.abort("internetdisconnected");
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      status: "PENDING_APPROVAL", classroomName, journeysAvailable: false }) });
  });

  await page.goto("/learn?account=1#ingresso=" + token);
  await expect(page.getByText("O professor precisará aprovar seu ingresso.")).toBeVisible();
  await page.getByRole("button", { name: "Confirmar e entrar na turma" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Sem conexão" })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem("ramuvi:pending-join-link"))).toBe(token);
  await page.getByRole("button", { name: "Tentar novamente" }).click();
  await expect(page.getByRole("status").filter({ hasText: "foi enviado ao professor" }))
    .toContainText("Nenhum cadastro foi criado ou vinculado");
  expect(attempts).toBe(2);
});

test("código administrativo da turma é recusado no cliente e convite individual segue como exceção", async ({ page }) => {
  await mockAccount(page, { authenticated: true, linked: false });
  let calls = 0;
  for (const path of ["preview", "accept"]) {
    await page.route("**/api/student-account/classroom-links/" + path, route => { calls++; return route.abort(); });
  }
  await page.goto("/learn?account=1");
  await page.getByRole("textbox", { name: "Link ou código de ingresso da turma" }).fill("TURMA2026");
  await page.getByRole("button", { name: "Continuar" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "32 caracteres" })).toBeVisible();
  expect(calls).toBe(0);
  await expect(page.getByText("Tenho um convite individual do professor")).toBeVisible();
});
