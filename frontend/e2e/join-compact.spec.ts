import { expect, test } from "./fixtures";

test("/join opens code entry without duplicated accessibility toolbar", async ({ page }) => {
  await page.goto("/join");
  await expect(page.getByRole("heading", { name: "Código de acesso" })).toBeVisible();
  await expect(page.locator(".student-accessibility-row")).toHaveCount(0);
  await expect(page.getByText("Reduzir movimento")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Continuar" })).toBeVisible();
});
