import { expect, test } from "./fixtures";

test("primeiro acesso mantém código; dispositivo lembrado oferece atalhos de retorno", async ({ page }) => {
  await page.goto("/join");
  await expect(page.getByRole("textbox", { name: "Código da sessão" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Continuar meus estudos" })).toHaveCount(0);

  await page.evaluate(() => localStorage.setItem("arena-dev-device:classroom-test", "token-sintetico-so-ui"));
  await page.reload();
  await expect(page.getByRole("link", { name: "Continuar meus estudos" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Aulas das minhas turmas" })).toHaveAttribute("href", "/learn/live");
  await expect(page.getByRole("textbox", { name: "Código da sessão" })).toBeVisible();

  await page.getByRole("link", { name: "Aulas das minhas turmas" }).click();
  await expect(page).toHaveURL(/\/learn\/live/);
  await expect(page.getByText("Nenhuma aula ao vivo encontrada neste momento.")).toBeVisible();
  await expect(page.getByRole("textbox", { name: "Código da sessão" })).toBeVisible();
});
