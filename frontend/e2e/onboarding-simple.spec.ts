import { expect, test } from "@playwright/test";

test.use({ serviceWorkers: "block" });
const classroomCode = "JOIN2026AA";
const classroomName = "Turma Desenvolvimento";
const accountId = "11111111-1111-4111-8111-111111111111";
const studentId = "22222222-2222-4222-8222-222222222222";

test("link de ingresso conserva o código após login Google e o resgata uma vez", async ({ page }) => {
  let authenticated = false;
  let linked = false;
  const captured: unknown[] = [];
  await page.route("**/api/student-account/providers", route => route.fulfill({
    status: 200, contentType: "application/json",
    body: JSON.stringify([{ id: "google", name: "Google",
      path: "/api/student-oidc/authorization/google" }]),
  }));
  await page.route("**/api/student-account/session", route => route.fulfill(
    authenticated ? { status: 200, contentType: "application/json",
      body: JSON.stringify({ accountId, studentId: linked ? studentId : null, role: "STUDENT" }) }
      : { status: 401 },
  ));
  await page.route("**/api/student-account/classrooms", route => route.fulfill({
    status: 200, contentType: "application/json",
    body: JSON.stringify(linked ? [{ enrollmentId: "e1", classroomId: "c1",
      classroomName, studentStudyAccessEnabled: true }] : []),
  }));
  await page.route("**/api/auth/csrf", route => route.fulfill({
    status: 200, contentType: "application/json", body: '{"token":"csrf-mock"}',
  }));
  await page.route("**/api/student-account/classrooms/join", async route => {
    captured.push(route.request().postDataJSON());
    expect(route.request().method()).toBe("POST");
    expect(route.request().url()).not.toContain(classroomCode);
    linked = true;
    return route.fulfill({ status: 200, contentType: "application/json",
      body: JSON.stringify({ studentId, enrollmentId: "e1", classroomId: "c1", classroomName }) });
  });

  await page.goto("/learn?account=1&classCode=" + classroomCode);
  await expect(page.getByRole("heading", { name: "Acesse sua conta" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Continuar com Google" })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem("ramuvi:pending-class-code")))
    .toBe(classroomCode);

  authenticated = true;
  await page.goto("/learn?account=1"); // Simula retorno seguro do provedor OIDC.
  await expect(page.getByRole("status").filter({ hasText: "Você entrou na turma" })).toBeVisible();
  expect(captured).toEqual([{ code: classroomCode }]);
  await expect(page.getByRole("heading", { name: "Minha conta" })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem("ramuvi:pending-class-code"))).toBeNull();
  await page.reload();
  expect(captured).toHaveLength(1);
});

test("código de turma compartilhado é alternativa curta ao convite individual", async ({ page }) => {
  await page.route("**/api/student-account/session", route => route.fulfill({
    status: 200, contentType: "application/json",
    body: JSON.stringify({ accountId, studentId: null, role: "STUDENT" }),
  }));
  await page.route("**/api/student-account/providers", route => route.fulfill({
    status: 200, contentType: "application/json", body: "[]",
  }));
  await page.route("**/api/student-account/classrooms", route => route.fulfill({
    status: 200, contentType: "application/json", body: "[]",
  }));
  await page.route("**/api/auth/csrf", route => route.fulfill({
    status: 200, contentType: "application/json", body: '{"token":"csrf-mock"}',
  }));
  let count = 0;
  await page.route("**/api/student-account/classrooms/join", async route => {
    count++;
    expect(route.request().postDataJSON()).toEqual({ code: classroomCode });
    return route.fulfill({ status: 400 });
  });
  await page.goto("/learn?account=1");
  await expect(page.getByRole("heading", { name: "Minha conta" })).toBeVisible();
  await page.getByRole("textbox", { name: "Código da turma" }).fill(classroomCode.toLowerCase());
  await page.getByRole("button", { name: "Entrar na turma" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "Não foi possível ingressar" }))
    .toContainText("Confirme o código");
  expect(count).toBe(1);
  await expect(page.getByText("Tenho um convite individual do professor")).toBeVisible();
});
