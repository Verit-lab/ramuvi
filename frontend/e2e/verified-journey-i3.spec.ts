import { expect, test, type BrowserContext } from "@playwright/test";

test.use({ serviceWorkers: "block" });

const journeyId = "11111111-1111-4111-8111-111111111111";
const contentId = "22222222-2222-4222-8222-222222222222";
const classroomId = "33333333-3333-4333-8333-333333333333";

const session = {
  accountId: "44444444-4444-4444-8444-444444444444",
  studentId: "55555555-5555-4555-8555-555555555555",
  role: "STUDENT",
};

function journeyView(completed: boolean) {
  return {
    id: journeyId,
    title: "Jornada entre dispositivos",
    description: "Mesma matrícula e histórico.",
    progress: { completedContents: completed ? 1 : 0, totalContents: 1, percent: completed ? 100 : 0 },
    releasedChapterCount: null,
    newlyUnlockedAchievements: [],
    modules: [{
      id: null, title: "Módulo 01", description: null,
      contents: [{
        id: contentId, title: "Conteúdo único", description: null,
        state: completed ? "COMPLETED" : "CURRENT",
        resourceId: null, hasExternalSource: false,
      }],
    }],
  };
}

function summary(completed: boolean) {
  return {
    id: journeyId, title: "Jornada entre dispositivos", description: null,
    progress: { completedContents: completed ? 1 : 0, totalContents: 1, percent: completed ? 100 : 0 },
    currentContentId: completed ? null : contentId,
    currentContentTitle: completed ? null : "Conteúdo único",
    started: completed, lastStudiedAt: null,
    classroomId, classroomName: "Turma autenticada", classroomCode: "I3-TURMA",
  };
}

async function mockAccount(context: BrowserContext, state: { completed: boolean; writes: number; legacy: number }) {
  await context.route("**/api/student-account/session", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(session) });
  });
  await context.route("**/api/student-account/study/journeys", async (route) => {
    await route.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify([summary(state.completed)]),
    });
  });
  await context.route(`**/api/student-account/study/journeys/${journeyId}`, async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(journeyView(state.completed)) });
  });
  await context.route(`**/api/student-account/study/journeys/${journeyId}/contents/${contentId}`, async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      id: contentId, title: "Conteúdo único", description: null,
      state: state.completed ? "COMPLETED" : "CURRENT", resourceId: null,
      body: "# Material da turma", format: "MARKDOWN",
      sourceUrl: null, sourceLabel: null, hasExternalSource: false, copyProtected: false,
    }) });
  });
  await context.route(`**/api/student-account/study/journeys/${journeyId}/contents/${contentId}/start`, async (route) => {
    expect(route.request().method()).toBe("POST");
    expect(route.request().headers()["x-study-token"]).toBeUndefined();
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(journeyView(state.completed)) });
  });
  await context.route(`**/api/student-account/study/journeys/${journeyId}/contents/${contentId}/complete`, async (route) => {
    expect(route.request().method()).toBe("POST");
    expect(route.request().headers()["x-study-token"]).toBeUndefined();
    expect(route.request().headers()["x-xsrf-token"]).toBe("csrf-for-i3");
    state.completed = true;
    state.writes += 1;
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(journeyView(true)) });
  });
  await context.route("**/api/auth/csrf", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ token: "csrf-for-i3" }) });
  });
  await context.route("**/api/learn/journeys**", async (route) => {
    state.legacy += 1;
    await route.fulfill({ status: 401 });
  });
}

test("I3: conta verificada lista a matrícula sem ler token de dispositivo", async ({ page }) => {
  const state = { completed: false, writes: 0, legacy: 0 };
  await mockAccount(page.context(), state);
  await page.addInitScript(() => {
    window.localStorage.setItem("arena-dev-device:33333333-3333-4333-8333-333333333333", "legacy-token");
  });
  await page.goto("/learn/journeys");
  await expect(page.getByText("Jornada entre dispositivos")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Turma autenticada" })).toBeVisible();
  await expect(page.getByText("Acesso pela sua conta verificada.")).toBeVisible();
  await expect(page.getByRole("button", { name: /Adicionar com código/ })).toHaveCount(0);
  expect(state.legacy).toBe(0);
});

test("I3: com feature desligada, o ingresso legado por código continua funcional", async ({ page }) => {
  await page.route("**/api/student-account/session", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(session) }));
  await page.route("**/api/student-account/study/journeys", (route) =>
    route.fulfill({ status: 404 }));
  await page.route("**/api/learn/journeys", async (route) => {
    expect(route.request().headers()["x-study-token"]).toBe("legacy-token");
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([summary(false)]) });
  });
  await page.addInitScript(() => {
    window.localStorage.setItem("arena-dev-device:33333333-3333-4333-8333-333333333333", "legacy-token");
  });
  await page.goto("/learn/journeys");
  await expect(page.getByText("Jornada entre dispositivos")).toBeVisible();
  await expect(page.getByRole("button", { name: /Adicionar com código/ })).toBeVisible();
});

test("I3: resposta 404 na conta não recorre ao token local", async ({ page }) => {
  const state = { completed: false, writes: 0, legacy: 0 };
  await mockAccount(page.context(), state);
  await page.route(`**/api/student-account/study/journeys/${journeyId}`, (route) => route.fulfill({ status: 404 }));
  await page.addInitScript(() => {
    window.localStorage.setItem("arena-dev-device:33333333-3333-4333-8333-333333333333", "legacy-token");
  });
  await page.goto(`/learn/journeys/${journeyId}`);
  await expect(page.getByText("Jornada não encontrada ou não autorizada para esta matrícula.")).toBeVisible();
  expect(state.legacy).toBe(0);
});

test("I3: conclusão por OIDC é recuperada em segundo navegador sem token local", async ({ browser }) => {
  const state = { completed: false, writes: 0, legacy: 0 };
  const first = await browser.newContext({ serviceWorkers: "block" });
  const second = await browser.newContext({ serviceWorkers: "block" });
  try {
    await mockAccount(first, state);
    await mockAccount(second, state);
    const a = await first.newPage();
    await a.goto(`/learn/journeys/${journeyId}/content/${contentId}`);
    await expect(a.getByRole("button", { name: /Concluir/ })).toBeVisible();
    await a.getByRole("button", { name: /Concluir/ }).click();
    await expect(a).toHaveURL(new RegExp(`/learn/journeys/${journeyId}$`));
    const b = await second.newPage();
    await b.goto(`/learn/journeys/${journeyId}`);
    await expect(b.getByText("Você concluiu todos os conteúdos.")).toBeVisible();
    expect(state.writes).toBe(1);
    expect(state.legacy).toBe(0);
  } finally {
    await first.close();
    await second.close();
  }
});

test("I3: sessão OIDC revogada não assume o token lembrado de outro aluno", async ({ page }) => {
  const state = { completed: false, writes: 0, legacy: 0 };
  await mockAccount(page.context(), state);
  await page.addInitScript(() => {
    window.localStorage.setItem("arena-dev-device:33333333-3333-4333-8333-333333333333", "another-student-token");
  });
  await page.goto("/learn/journeys");
  await expect(page.getByText("Jornada entre dispositivos")).toBeVisible();
  // The first successful OIDC response established the verified identity boundary.
  expect(await page.evaluate(() => localStorage.getItem("ramuvi:student-account-observed"))).toBe("1");
  await page.route("**/api/student-account/session", route => route.fulfill({ status: 401 }));
  await page.reload();
  await expect(page.getByText("Não foi possível carregar suas Jornadas.")).toBeVisible();
  expect(state.legacy).toBe(0);
});

test("I3: aluno pode escolher explicitamente voltar ao fluxo legado", async ({ page }) => {
  const state = { completed: false, writes: 0, legacy: 0 };
  await mockAccount(page.context(), state);
  await page.addInitScript(() => {
    window.localStorage.setItem("arena-dev-device:33333333-3333-4333-8333-333333333333", "legacy-token");
  });
  await page.goto("/learn/journeys");
  await expect(page.getByText("Jornada entre dispositivos")).toBeVisible();
  await page.route("**/api/student-account/session", route => route.fulfill({ status: 401 }));
  await page.route("**/api/student-account/providers", route =>
    route.fulfill({ status: 200, contentType: "application/json",
      body: JSON.stringify([{ id: "google", name: "Google", path: "/api/student-oidc/authorization/google" }]) }));
  await page.goto("/learn/login");
  await expect(page.getByRole("alert").filter({ hasText: "Sua sessão de conta" })).toContainText("encerrada ou revogada");
  await expect(page.getByRole("link", { name: "Continuar com Google" })).toBeVisible();
  await page.getByRole("button", { name: "Continuar explicitamente com acesso por código" }).click();
  await expect(page).toHaveURL(/\/learn\/journeys$/);
  // No silent fallback: the device identity was selected through an explicit user action.
  expect(await page.evaluate(() => localStorage.getItem("ramuvi:student-account-observed"))).toBeNull();
  await expect.poll(() => state.legacy).toBeGreaterThan(0);
});

test("I3: logout mantém barreira de conta até escolha explícita do modo por código", async ({ page }) => {
  const state = { completed: false, writes: 0, legacy: 0 };
  await mockAccount(page.context(), state);
  await page.route("**/api/student-account/providers", route =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }));
  await page.route("**/api/student-account/classrooms", route =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }));
  await page.route("**/api/student-account/logout", route => {
    expect(route.request().method()).toBe("POST");
    return route.fulfill({ status: 204 });
  });
  await page.addInitScript(() => {
    window.localStorage.setItem("arena-dev-device:33333333-3333-4333-8333-333333333333", "other-student-token");
  });
  await page.goto("/learn/login");
  await expect(page.getByRole("button", { name: "Sair desta conta" })).toBeVisible();
  await page.getByRole("button", { name: "Sair desta conta" }).click();
  await expect(page.getByRole("button", { name: "Continuar explicitamente com acesso por código" })).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem("ramuvi:student-account-observed"))).toBe("1");
  await page.route("**/api/student-account/session", route => route.fulfill({ status: 401 }));
  await page.goto("/learn/journeys");
  await expect(page.getByText("Não foi possível carregar suas Jornadas.")).toBeVisible();
  expect(state.legacy).toBe(0);
});
