import { expect, request, test, type APIResponse } from "./fixtures";

const apiBase = process.env.E2E_API_URL ?? "http://127.0.0.1:8080";
const browserApi = process.env.E2E_BROWSER_API_URL ?? "http://localhost:8080";
const username = process.env.E2E_TEACHER_USERNAME ?? "professor";
const password = process.env.E2E_TEACHER_PASSWORD ?? "arena-dev-change-me";

async function assertOk(response: APIResponse, context: string) {
  expect(response.ok(), context + ": " + response.status() + " " + await response.text()).toBe(true);
}

test("Atividades: editor na página e importação JSON validada sem modal de criação", async ({ page }) => {
  // Only against the disposable Compose E2E, never a real teaching environment.
  const api = await request.newContext({ baseURL: apiBase });
  const stamp = Date.now().toString(36);
  const className = "Atividade QA " + stamp;
  let classroomId: string | undefined;
  let headers: Record<string, string> | undefined;
  try {
    const csrf = await api.get("/api/auth/csrf");
    await assertOk(csrf, "CSRF");
    headers = { "X-XSRF-TOKEN": ((await csrf.json()) as { token: string }).token };
    await assertOk(await api.post("/api/auth/login", { data: { username, password }, headers }), "Login");
    const classroomResponse = await api.post("/api/classrooms", {
      headers, data: { name: className, code: "QA-" + stamp.slice(-6) },
    });
    await assertOk(classroomResponse, "Turma QA");
    classroomId = ((await classroomResponse.json()) as { id: string }).id;

    const csrfBrowser = await page.request.get(browserApi + "/api/auth/csrf");
    await assertOk(csrfBrowser, "CSRF browser");
    await assertOk(await page.request.post(browserApi + "/api/auth/login", {
      data: { username, password },
      headers: { "X-XSRF-TOKEN": ((await csrfBrowser.json()) as { token: string }).token },
    }), "Login browser");

    await page.goto("/");
    await page.getByRole("button", { name: "Abrir turma " + className }).click();
    await page.locator(".classroom-section-tab").filter({ hasText: "Atividades" }).click();

    await page.getByRole("button", { name: "+ Nova atividade" }).click();
    const newEditor = page.getByRole("region", { name: "Criação de atividade" });
    await expect(newEditor).toBeVisible();
    await expect(page.getByRole("dialog", { name: "Nova atividade" })).toHaveCount(0);
    await expect(newEditor.getByLabel("Título")).toBeVisible();
    await expect(newEditor.getByRole("navigation", { name: "Etapas do cadastro" })).toBeVisible();
    await expect(newEditor.getByRole("button", { name: /Continuar para questões/ })).toBeVisible();
    await newEditor.getByLabel("Título").fill("Atividade de teste");
    await newEditor.getByRole("button", { name: /Continuar para questões/ }).click();
    await expect(newEditor.getByRole("button", { name: /Criar manualmente/ })).toHaveAttribute("aria-pressed", "true");
    await expect(newEditor.getByText("Como você deseja preparar as questões?")).toBeVisible();
    await newEditor.getByLabel("Enunciado").fill("Questão ainda em edição");
    await newEditor.getByRole("button", { name: /Conferir atividade/ }).click();
    await expect(newEditor.getByRole("heading", { name: "Confira antes de salvar" })).toHaveCount(0);
    page.once("dialog", dialog => dialog.accept());
    await newEditor.getByRole("button", { name: /Voltar às atividades/ }).click();
    await expect(page.getByRole("heading", { name: "Atividades da turma" })).toBeVisible();

    const packageJson = JSON.stringify({
      version: "1.0",
      activity: { title: "Revisão SQL", topic: "Banco de Dados" },
      questions: [
        { type: "OPEN", difficulty: "INTERMEDIATE", points: 2,
          statement: "Explique a primeira forma normal.", expectedAnswer: "Valores atômicos." },
      ],
    });
    await page.locator('input[type="file"][accept*=".json"]').setInputFiles({
      name: "questoes.json", mimeType: "application/json", buffer: Buffer.from(packageJson),
    });
    const editor = page.getByRole("region", { name: "Criação de atividade" });
    await expect(editor).toBeVisible();
    await expect(editor.getByText(/questão importada do arquivo/)).toBeVisible();
    await editor.getByRole("button", { name: /Dados da atividade/ }).click();
    await expect(editor.getByLabel("Título")).toHaveValue("Revisão SQL");
    await editor.getByRole("button", { name: /Conferir e salvar/ }).click();
    await expect(editor.getByRole("heading", { name: "Confira antes de salvar" })).toBeVisible();
    await editor.getByRole("button", { name: "Salvar atividade" }).click();
    await expect(page.getByRole("heading", { name: "Revisão SQL" })).toBeVisible();

    await page.getByRole("button", { name: "Copiar de outra turma" }).click();
    await expect(page.getByRole("dialog", { name: "Importar atividade de outra turma" })).toBeVisible();
  } finally {
    if (classroomId && headers) {
      await api.delete("/api/classrooms/" + classroomId, { headers, data: { confirmationName: className } });
    }
    await api.dispose();
  }
});

