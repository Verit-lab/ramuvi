import { expect, test } from "@playwright/test";

test.use({ serviceWorkers: "block" });

const account = {
  accountId: "11111111-1111-4111-8111-111111111111",
  studentId: "22222222-2222-4222-8222-222222222222",
  role: "STUDENT",
};
const providers = [{ id: "google", name: "Google", path: "/api/student-oidc/authorization/google" }];
const session = {
  sessionId: "44444444-4444-4444-8444-444444444444",
  classroomId: "33333333-3333-4333-8333-333333333333",
  classroomName: "Turma A",
  sessionTitle: "Aula em andamento",
  joinCode: "ABCDEF",
  startedAt: "2026-11-18T21:00:00Z",
};

test("logout voluntário não confunde revogação e não libera token de outro aluno", async ({ page }) => {
  let signedOut = false;
  let legacyCalls = 0;
  await page.addInitScript(() => {
    localStorage.setItem("arena-dev-device:33333333-3333-4333-8333-333333333333", "another-student-token");
  });
  await page.route("**/api/student-account/session", (route) =>
    route.fulfill(signedOut ? { status: 401 } : {
      status: 200, contentType: "application/json", body: JSON.stringify(account),
    }));
  await page.route("**/api/student-account/classrooms", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }));
  await page.route("**/api/student-account/providers", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(providers) }));
  await page.route("**/api/auth/csrf", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: '{"token":"csrf-mock"}' }));
  await page.route("**/api/student-account/logout", async (route) => {
    expect(route.request().method()).toBe("POST");
    signedOut = true;
    await route.fulfill({ status: 204 });
  });
  await page.route("**/api/learn/journeys**", async (route) => {
    legacyCalls += 1;
    await route.fulfill({ status: 401 });
  });

  await page.goto("/learn?account=1");
  await expect(page.getByRole("heading", { name: "Minha conta" })).toBeVisible();
  await page.getByRole("button", { name: "Sair desta conta" }).click();
  await expect(page.getByText("Você saiu da sua conta com segurança.")).toBeVisible();
  await expect(page.getByRole("alert").filter({ hasText: "encerrada ou revogada" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Continuar explicitamente com acesso por código" })).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem("ramuvi:student-account-observed"))).toBe("1");
  expect(await page.evaluate(() => localStorage.getItem("ramuvi:student-account-signed-out"))).toBe("1");

  await page.reload();
  await expect(page.getByText("Você saiu da sua conta com segurança.")).toBeVisible();
  await page.goto("/learn/journeys");
  await expect(page.getByText("Não foi possível carregar suas Jornadas.")).toBeVisible();
  expect(legacyCalls).toBe(0);
});

test("sessão revogada sem logout continua exigindo novo login", async ({ page }) => {
  let revoked = false;
  await page.route("**/api/student-account/session", (route) =>
    route.fulfill(revoked ? { status: 401 } : {
      status: 200, contentType: "application/json", body: JSON.stringify(account),
    }));
  await page.route("**/api/student-account/classrooms", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }));
  await page.route("**/api/student-account/providers", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(providers) }));
  await page.goto("/learn?account=1");
  await expect(page.getByRole("heading", { name: "Minha conta" })).toBeVisible();
  revoked = true;
  await page.reload();
  await expect(page.getByRole("alert").filter({ hasText: "encerrada ou revogada" })).toBeVisible();
  await expect(page.getByText("Você saiu da sua conta com segurança.")).toHaveCount(0);
});

test("vínculo por convite orienta primeiro acesso e apresenta erro de limite de tentativas", async ({ page }) => {
  let claimed = false;
  let attempts = 0;
  const code = "A".repeat(43);
  await page.route("**/api/student-account/session", (route) =>
    route.fulfill({ status: 200, contentType: "application/json",
      body: JSON.stringify({ ...account, studentId: claimed ? account.studentId : null }) }));
  await page.route("**/api/student-account/classrooms", (route) =>
    route.fulfill({ status: 200, contentType: "application/json",
      body: JSON.stringify(claimed ? [{ enrollmentId: "e1", classroomId: "c1",
        classroomName: "Turma A", studentStudyAccessEnabled: true }] : []) }));
  await page.route("**/api/student-account/providers", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(providers) }));
  await page.route("**/api/auth/csrf", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: '{"token":"csrf-mock"}' }));
  await page.route("**/api/student-account/enrollments/claim", async (route) => {
    attempts += 1;
    expect(route.request().method()).toBe("POST");
    expect(route.request().url()).not.toContain(code);
    expect(route.request().postDataJSON()).toEqual({ token: code });
    if (attempts === 1) return route.fulfill({ status: 429 });
    claimed = true;
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
      studentId: account.studentId, enrollmentId: "e1", classroomId: "c1", classroomName: "Turma A",
    }) });
  });
  await page.route("**/api/student-account/study/journeys", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }));

  await page.goto("/learn");
  await expect(page.getByRole("heading", { name: "Minha conta" })).toBeVisible();
  await expect(page.getByRole("list", { name: "Etapas do primeiro acesso" })).toBeVisible();
  await expect(page.getByRole("textbox", { name: "Link ou código de ingresso da turma" })).toBeVisible();
  await page.getByText("Tenho um convite individual do professor").click();
  await expect(page.getByText("Ele é diferente do link de ingresso da turma.", { exact: false })).toBeVisible();
  const button = page.getByRole("button", { name: "Vincular matrícula" });
  await expect(button).toBeDisabled();
  await page.getByLabel("Código individual do convite").fill(code);
  await expect(button).toBeEnabled();
  await button.click();
  await expect(page.getByRole("alert").filter({ hasText: "Muitas tentativas" })).toBeVisible();
  await button.click();
  await expect(page.getByRole("status").filter({ hasText: "Convite confirmado" })).toBeVisible();
  await page.getByRole("link", { name: "Ir para meu espaço de estudo" }).click();
  await expect(page).toHaveURL(/\/learn$/);
  await expect(page.getByRole("heading", { name: "Seu espaço de estudo" })).toBeVisible();
});

test("home do aluno diferencia falha de aulas ao vivo e permite tentar novamente", async ({ page }) => {
  let attempts = 0;
  await page.addInitScript(() => {
    localStorage.setItem("arena-dev-device:33333333-3333-4333-8333-333333333333", "device-token");
  });
  await page.route("**/api/student-account/session", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(account) }));
  await page.route("**/api/student-account/study/journeys", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }));
  await page.route("**/api/learn/live", async (route) => {
    attempts += 1;
    return route.fulfill(attempts === 1 ? { status: 503 } : {
      status: 200, contentType: "application/json", body: JSON.stringify([session]),
    });
  });
  await page.goto("/learn");
  await expect(page.getByRole("alert").filter({ hasText: "Não foi possível consultar suas aulas ao vivo" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Seu espaço de estudo" })).toBeVisible();
  await page.getByRole("button", { name: "Tentar novamente" }).click();
  await expect(page.getByRole("region", { name: "Aula ao vivo" })).toContainText("Turma A");
  await expect(page.getByRole("alert").filter({ hasText: "Não foi possível consultar" })).toHaveCount(0);
  expect(attempts).toBe(2);
});

test("hub ao vivo apresenta falha e permite tentar novamente", async ({ page }) => {
  let attempts = 0;
  await page.addInitScript(() => {
    localStorage.setItem("arena-dev-device:33333333-3333-4333-8333-333333333333", "device-token");
  });
  await page.route("**/api/learn/live", async (route) => {
    attempts += 1;
    return route.fulfill(attempts === 1 ? { status: 503 } : {
      status: 200, contentType: "application/json", body: "[]",
    });
  });
  await page.goto("/learn/live");
  await expect(page.getByRole("alert").filter({ hasText: "Não foi possível consultar as aulas ao vivo" })).toBeVisible();
  await expect(page.getByText("Nenhuma aula ao vivo encontrada neste momento.")).toHaveCount(0);
  await page.getByRole("button", { name: "Tentar novamente" }).click();
  await expect(page.getByText("Nenhuma aula ao vivo encontrada neste momento.")).toBeVisible();
  await expect(page.getByRole("alert").filter({ hasText: "Não foi possível consultar" })).toHaveCount(0);
  expect(attempts).toBe(2);
});

test("consultas parciais preservam aulas de turma válida quando outra credencial expirou", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("arena-dev-device:33333333-3333-4333-8333-333333333333", "expired-token");
    localStorage.setItem("arena-dev-device:44444444-4444-4444-8444-444444444444", "valid-token");
  });
  await page.route("**/api/learn/live", (route) =>
    route.fulfill(route.request().headers()["x-study-token"] === "expired-token"
      ? { status: 401 }
      : { status: 200, contentType: "application/json", body: JSON.stringify([session]) }));
  await page.goto("/learn/live");
  await expect(page.getByRole("heading", { name: "Sua aula está ao vivo" })).toBeVisible();
  await expect(page.getByRole("alert").filter({ hasText: "Não foi possível consultar" })).toHaveCount(0);
});


test("novo login verificado elimina marcador de saída voluntária", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("ramuvi:student-account-observed", "1");
    localStorage.setItem("ramuvi:student-account-signed-out", "1");
  });
  await page.route("**/api/student-account/session", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(account) }));
  await page.route("**/api/student-account/classrooms", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }));
  await page.route("**/api/student-account/providers", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(providers) }));
  await page.goto("/learn?account=1");
  await expect(page.getByRole("heading", { name: "Minha conta" })).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem("ramuvi:student-account-observed"))).toBe("1");
  expect(await page.evaluate(() => localStorage.getItem("ramuvi:student-account-signed-out"))).toBeNull();
  await expect(page.getByText("Você saiu da sua conta com segurança.")).toHaveCount(0);
});

test("erro ao carregar Jornadas permite repetir sem perder o acesso ao vivo", async ({ page }) => {
  let attempts = 0;
  await page.route("**/api/student-account/session", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(account) }));
  await page.route("**/api/student-account/study/journeys", async (route) => {
    attempts += 1;
    return route.fulfill(attempts === 1
      ? { status: 503 }
      : { status: 200, contentType: "application/json", body: "[]" });
  });
  await page.goto("/learn");
  await expect(page.getByRole("alert").filter({ hasText: "Não foi possível abrir seu espaço de estudo." })).toBeVisible();
  await expect(page.getByRole("link", { name: "Entrar em uma aula com código" })).toBeVisible();
  await page.getByRole("button", { name: "Tentar novamente" }).click();
  await expect(page.getByText("Seu espaço de estudo ainda está vazio.")).toBeVisible();
  await expect(page.getByRole("alert").filter({ hasText: "Não foi possível abrir" })).toHaveCount(0);
  expect(attempts).toBe(2);
});
