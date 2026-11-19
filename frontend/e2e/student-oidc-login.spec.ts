import { expect, test } from "@playwright/test";

// API responses in these focused browser tests are mocked at the page network
// layer. PWA service workers may intercept requests before Playwright routes.
test.use({ serviceWorkers: "block" });

test("v0.12 / I1: aluno mantém ingresso por código quando OIDC está desligado", async ({ page }) => {
  await page.route("**/api/student-account/providers", async (route) => {
    await route.fulfill({ status: 404 });
  });
  await page.route("**/api/student-account/session", async (route) => {
    await route.fulfill({ status: 401 });
  });
  await page.goto("/learn");
  await expect(page.getByRole("heading", { name: "Acesse sua conta" })).toBeVisible();
  await expect(page.getByText("O acesso por conta ainda não foi habilitado")).toBeVisible();
  await expect(page.getByRole("link", { name: "Entrar em uma aula com código" }))
    .toHaveAttribute("href", "/join");
});

test("v0.12 / I1: login OIDC usa apenas links de provedores conhecidos", async ({ page }) => {
  await page.route("**/api/student-account/providers", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([
        { id: "google", name: "Google", path: "/api/student-oidc/authorization/google" },
        { id: "microsoft", name: "Microsoft", path: "https://phish.example/steal" },
      ]),
    });
  });
  await page.route("**/api/student-account/session", async (route) => {
    await route.fulfill({ status: 401 });
  });
  await page.goto("/learn");
  await expect(page.getByRole("link", { name: "Continuar com Google" }))
    .toHaveAttribute("href", "/api/student-oidc/authorization/google");
  await expect(page.getByRole("link", { name: "Continuar com Microsoft" })).toHaveCount(0);
});

test("v0.12 / I1: conta verificada não libera matrícula sem convite", async ({ page }) => {
  await page.route("**/api/student-account/providers", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
  });
  await page.route("**/api/student-account/session", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        accountId: "11111111-1111-4111-8111-111111111111",
        studentId: null,
        role: "STUDENT",
      }),
    });
  });
  await page.goto("/learn");
  await expect(page.getByText("Conta verificada. Abra o link de ingresso da turma")).toBeVisible();
  await expect(page.getByRole("button", { name: "Sair desta conta" })).toBeVisible();
});

test("v0.12 / I2: resgate usa POST, limpa token e revela apenas as turmas ativas", async ({ page }) => {
  const token = "A".repeat(43);
  let claimed = false;
  await page.route("**/api/student-account/providers", async (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }));
  await page.route("**/api/student-account/session", async (route) =>
    route.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify({ accountId: "11111111-1111-4111-8111-111111111111",
        studentId: claimed ? "22222222-2222-4222-8222-222222222222" : null, role: "STUDENT" }),
    }));
  await page.route("**/api/student-account/classrooms", async (route) =>
    route.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify(claimed ? [
        { enrollmentId: "a", classroomId: "c", classroomName: "Banco de Dados", studentStudyAccessEnabled: true },
        { enrollmentId: "b", classroomId: "d", classroomName: "Redes", studentStudyAccessEnabled: false },
      ] : []),
    }));
  await page.route("**/api/auth/csrf", async (route) =>
    route.fulfill({ status: 200, contentType: "application/json",
      body: JSON.stringify({ token: "csrf-for-test" }) }));
  await page.route("**/api/student-account/enrollments/claim", async (route) => {
    expect(route.request().method()).toBe("POST");
    expect(route.request().url()).not.toContain(token);
    expect(route.request().postDataJSON()).toEqual({ token });
    expect(route.request().headers()["x-xsrf-token"]).toBe("csrf-for-test");
    claimed = true;
    await route.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify({ studentId: "22222222-2222-4222-8222-222222222222",
        enrollmentId: "a", classroomId: "c", classroomName: "Banco de Dados" }),
    });
  });

  await page.goto("/learn");
  await page.getByText("Tenho um convite individual do professor").click(); // exceção, recolhida
  const input = page.getByRole("textbox", { name: "Código individual do convite" });
  await expect(input).toBeVisible();
  await input.fill(token);
  await page.getByRole("button", { name: "Vincular matrícula" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Convite confirmado" })).toBeVisible();
  await expect(page.getByText("Banco de Dados", { exact: true })).toBeVisible();
  await expect(page.getByText("Redes", { exact: true })).toBeVisible();
  await expect(input).toHaveValue("");
  expect(page.url()).not.toContain(token);
  expect(await page.evaluate((code) =>
    JSON.stringify({ session: { ...sessionStorage }, local: { ...localStorage } }).includes(code),
    token)).toBe(false);
});

test("v0.12 / I2: flag desligada não oferece resgate nem oculta a entrada por código", async ({ page }) => {
  await page.route("**/api/student-account/providers", async (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }));
  await page.route("**/api/student-account/session", async (route) =>
    route.fulfill({ status: 200, contentType: "application/json",
      body: JSON.stringify({ accountId: "11111111-1111-4111-8111-111111111111",
        studentId: null, role: "STUDENT" }) }));
  await page.route("**/api/student-account/classrooms", async (route) =>
    route.fulfill({ status: 404 }));
  await page.goto("/learn");
  await expect(page.getByText("O vínculo de matrícula ainda não foi habilitado")).toBeVisible();
  await expect(page.getByRole("textbox", { name: "Código individual do convite" })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Entrar em uma aula com código" }))
    .toHaveAttribute("href", "/join");
});
