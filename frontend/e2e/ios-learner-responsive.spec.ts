import { expect, test } from "./fixtures";

// Emulation protects regressions quickly; Safari on a physical iPhone is a
// separate mandatory gate for the stable v0.11.0 release.
test("student mobile Settings has one navigation entry and reflows without horizontal overflow", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/learn/profile");
  await expect(page.getByRole("heading", { name: "Configurações" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Abrir navegação" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Configurações e perfil" })).toHaveCount(0);
  await page.getByRole("button", { name: "Abrir navegação" }).click();
  const nav = page.getByRole("navigation", { name: "Navegação de estudos no celular" });
  await expect(nav.getByRole("link", { name: "Configurações" })).toBeVisible();
  await page.getByRole("button", { name: "Fechar navegação" }).click();
  await expect(nav).toHaveCount(0);
  await expect(page.getByText("Tamanho do conteúdo", { exact: true })).toBeVisible();
  await expect(page.getByLabel("Tema da interface")).toBeVisible();
  const metrics = await page.evaluate(() => ({
    overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    fontSize: parseFloat(getComputedStyle(document.querySelector('select[aria-label="Tema da interface"]')!).fontSize),
  }));
  expect(metrics.overflow).toBeLessThanOrEqual(2);
  expect(metrics.fontSize).toBeGreaterThanOrEqual(16);
});

test("student Journey code entry stays 16px+, tappable and contained in the viewport", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/learn/journeys");
  const add = page.getByRole("button", { name: /Adicionar com código/ });
  await expect(add).toBeVisible();
  await add.click();
  await expect(page.getByRole("button", { name: "Fechar formulário" })).toHaveAttribute("aria-expanded", "true");
  const input = page.getByRole("textbox", { name: "Código da Jornada" });
  await expect(input).toBeVisible();
  const dimensions = await input.evaluate((el) => ({
    fontSize: parseFloat(getComputedStyle(el).fontSize),
    height: el.getBoundingClientRect().height,
    left: el.getBoundingClientRect().left,
    right: el.getBoundingClientRect().right,
    viewport: document.documentElement.clientWidth,
    document: document.documentElement.scrollWidth,
  }));
  expect(dimensions.fontSize).toBeGreaterThanOrEqual(16);
  expect(dimensions.height).toBeGreaterThanOrEqual(44);
  expect(dimensions.left).toBeGreaterThanOrEqual(0);
  expect(dimensions.right).toBeLessThanOrEqual(dimensions.viewport + 1);
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport + 2);
  await page.getByRole("button", { name: "Fechar formulário" }).click();
  await expect(input).toHaveCount(0);
});
