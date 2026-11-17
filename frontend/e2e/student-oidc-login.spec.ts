import { expect, test } from "@playwright/test";

test("v0.12 / I1: aluno mantém ingresso por código quando OIDC está desligado", async ({ page }) => {
  await page.route("**/api/student-account/providers", async (route) => {
    await route.fulfill({ status: 404 });
  });
  await page.route("**/api/student-account/session", async (route) => {
    await route.fulfill({ status: 401 });
  });
  await page.goto("/learn/login");
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
  await page.goto("/learn/login");
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
  await page.goto("/learn/login");
  await expect(page.getByText("Sua identidade foi verificada.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Sair desta conta" })).toBeVisible();
});
