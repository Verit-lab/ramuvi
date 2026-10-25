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
    await expect(newEditor.getByRole("button", { name: /Voltar às atividades/ })).toHaveCount(0);
    await expect(newEditor.getByLabel("XP da entrega")).toHaveCount(0);
    await expect(newEditor.getByText("Configurações opcionais")).toHaveCount(0);
    await expect(newEditor.getByRole("navigation", { name: "Etapas do cadastro" })).toBeVisible();
    await page.setViewportSize({ width: 1600, height: 900 });
    const desktopTitle = await newEditor.getByLabel("Título").boundingBox();
    const desktopTopic = await newEditor.getByLabel("Tema / tópico").boundingBox();
    const desktopKind = await newEditor.getByLabel("Tipo").boundingBox();
    expect(desktopTitle && desktopTopic && desktopKind).toBeTruthy();
    if (desktopTitle && desktopTopic && desktopKind) {
      expect(Math.abs(desktopTitle.y - desktopTopic.y)).toBeLessThanOrEqual(3);
      expect(Math.abs(desktopTopic.y - desktopKind.y)).toBeLessThanOrEqual(3);
    }
    await page.setViewportSize({ width: 1024, height: 900 });
    const tabletTitle = await newEditor.getByLabel("Título").boundingBox();
    const tabletTopic = await newEditor.getByLabel("Tema / tópico").boundingBox();
    const tabletKind = await newEditor.getByLabel("Tipo").boundingBox();
    expect(tabletTitle && tabletTopic && tabletKind).toBeTruthy();
    if (tabletTitle && tabletTopic && tabletKind) {
      expect(tabletTopic.y).toBeGreaterThan(tabletTitle.y + tabletTitle.height);
      expect(Math.abs(tabletTopic.y - tabletKind.y)).toBeLessThanOrEqual(3);
    }
    await page.setViewportSize({ width: 390, height: 844 });
    const mobileTitle = await newEditor.getByLabel("Título").boundingBox();
    const mobileTopic = await newEditor.getByLabel("Tema / tópico").boundingBox();
    const mobileKind = await newEditor.getByLabel("Tipo").boundingBox();
    expect(mobileTitle && mobileTopic && mobileKind).toBeTruthy();
    if (mobileTitle && mobileTopic && mobileKind) {
      expect(mobileTopic.y).toBeGreaterThan(mobileTitle.y + mobileTitle.height);
      expect(mobileKind.y).toBeGreaterThan(mobileTopic.y + mobileTopic.height);
    }
    await page.setViewportSize({ width: 1600, height: 900 });
    await expect(newEditor.getByRole("button", { name: /Continuar para questões/ })).toBeVisible();
    await newEditor.getByLabel("Título").fill("Atividade de teste");
    await newEditor.getByRole("button", { name: /Continuar para questões/ }).click();
    await expect(newEditor.getByRole("button", { name: /Criar manualmente/ })).toHaveAttribute("aria-pressed", "true");
    await expect(newEditor.getByText("Como você deseja preparar as questões?")).toBeVisible();
    await newEditor.getByRole("textbox", { name: "Enunciado", exact: true }).fill("Questão ainda em edição");
    await newEditor.getByRole("button", { name: /Conferir atividade/ }).click();
    await expect(newEditor.getByRole("heading", { name: "Confira antes de salvar" })).toHaveCount(0);
    page.once("dialog", dialog => dialog.accept());
    await newEditor.getByRole("button", { name: "Cancelar" }).click();
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
    await expect(editor.getByRole("region", { name: "Configurações da atividade" })).toBeVisible();
    for (const width of [390, 1024, 1600, 1920, 2560]) {
      await page.setViewportSize({ width, height: 900 });
      const geometry = await editor.evaluate((root) => {
        const steps = root.querySelector<HTMLElement>(".activity-editor-steps")!.getBoundingClientRect();
        const review = root.querySelector<HTMLElement>(".activity-editor-review")!.getBoundingClientRect();
        const panels = Array.from(root.querySelector<HTMLElement>(".activity-editor-review-columns")!.children)
          .map((node) => node.getBoundingClientRect());
        return {
          stepsX: steps.x, stepsWidth: steps.width, reviewX: review.x, reviewWidth: review.width,
          panelY: panels.map((box) => box.y),
          overflow: document.documentElement.scrollWidth - window.innerWidth,
        };
      });
      expect(Math.abs(geometry.stepsX - geometry.reviewX), `alinhamento da revisão em ${width}px`).toBeLessThanOrEqual(2);
      expect(Math.abs(geometry.stepsWidth - geometry.reviewWidth), `largura da revisão em ${width}px`).toBeLessThanOrEqual(2);
      expect(geometry.overflow, `overflow em ${width}px`).toBeLessThanOrEqual(1);
      if (width > 1120) {
        expect(Math.abs(geometry.panelY[0] - geometry.panelY[1]), `duas colunas em ${width}px`).toBeLessThanOrEqual(2);
      } else {
        expect(geometry.panelY[1], `revisão empilhada em ${width}px`).toBeGreaterThan(geometry.panelY[0]);
      }
    }
    await page.setViewportSize({ width: 1600, height: 900 });
    await expect(editor.getByLabel("XP da entrega")).toHaveValue("10");
    await editor.getByLabel("XP da entrega").fill("15");
    await expect(editor.getByLabel("Bônus no prazo")).toHaveValue("5");
    await expect(editor.getByLabel("Tempo total da tentativa (minutos)")).toHaveValue("0");
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

