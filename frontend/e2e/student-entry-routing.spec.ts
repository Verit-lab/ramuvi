import { expect, test } from "@playwright/test";

test.use({ serviceWorkers: "block" });

test("anonymous learner lands on authentication at /learn without the study sidebar", async ({ page }) => {
  await page.route("**/api/student-account/session", route => route.fulfill({ status: 401 }));
  await page.route("**/api/student-account/providers", route => route.fulfill({
    status: 200, contentType: "application/json",
    body: JSON.stringify([{ id: "google", name: "Google", path: "/api/student-oidc/authorization/google" }]),
  }));
  await page.goto("/learn");

  await expect(page).toHaveURL(/\/learn$/);
  await expect(page.getByRole("heading", { name: "Acesse sua conta" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Continuar com Google" }))
    .toHaveAttribute("href", "/api/student-oidc/authorization/google");
  await expect(page.locator("aside")).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Entrar em uma aula com código" }))
    .toHaveAttribute("href", "/join");
});

test("verified learner sees their study dashboard and manages the account at the same /learn route", async ({ page }) => {
  await page.route("**/api/student-account/session", route => route.fulfill({
    status: 200, contentType: "application/json",
    body: JSON.stringify({ accountId: "11111111-1111-4111-8111-111111111111",
      studentId: "22222222-2222-4222-8222-222222222222", role: "STUDENT" }),
  }));
  await page.route("**/api/student-account/study/journeys", route => route.fulfill({
    status: 200, contentType: "application/json", body: "[]",
  }));
  await page.route("**/api/student-account/classrooms", route => route.fulfill({
    status: 200, contentType: "application/json", body: "[]",
  }));
  await page.route("**/api/student-account/providers", route => route.fulfill({
    status: 200, contentType: "application/json", body: "[]",
  }));

  await page.goto("/learn");
  await expect(page.getByRole("heading", { name: "Seu espaço de estudo" })).toBeVisible();
  const sidebar = page.locator("aside");
  if (await sidebar.isVisible()) {
    const accountLink = sidebar.getByRole("link", { name: "Minha conta" });
    await expect(accountLink).toHaveAttribute("href", "/learn?account=1");
    await accountLink.click();
  } else {
    // Mobile presents the account destination in its navigation drawer.
    await page.getByRole("button", { name: "Abrir navegação" }).click();
    const accountLink = page.getByRole("navigation", { name: "Navegação de estudos no celular" })
      .getByRole("link", { name: "Minha conta" });
    await expect(accountLink).toHaveAttribute("href", "/learn?account=1");
    await accountLink.click();
  }
  await expect(page).toHaveURL(/\/learn\?account=1$/);
  await expect(page.getByRole("heading", { name: "Minha conta" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Voltar ao meu espaço de estudo" })).toBeVisible();
  await page.getByRole("link", { name: "Voltar ao meu espaço de estudo" }).click();
  await expect(page).toHaveURL(/\/learn$/);
  await expect(page.getByRole("heading", { name: "Seu espaço de estudo" })).toBeVisible();
});

test("legacy login links redirect to canonical /learn and retain OAuth failure feedback", async ({ page }) => {
  await page.route("**/api/student-account/session", route => route.fulfill({ status: 401 }));
  await page.route("**/api/student-account/providers", route => route.fulfill({
    status: 200, contentType: "application/json", body: "[]",
  }));
  await page.goto("/learn/login?error=oidc");
  await expect(page).toHaveURL(/\/learn\?account=1&error=oidc$/);
  await expect(page.getByRole("alert").filter({ hasText: "O provedor não concluiu" })).toBeVisible();
});

test("live experiences remain available at /join without a student account", async ({ page }) => {
  await page.goto("/join");
  await expect(page).toHaveURL(/\/join$/);
  await expect(page.getByRole("heading", { name: "Acesse sua conta" })).toHaveCount(0);
});
