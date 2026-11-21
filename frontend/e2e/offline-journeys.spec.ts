import { expect, test } from "@playwright/test";

// Unlike account-only tests, this exercises the REAL PWA Service Worker.
test.use({ serviceWorkers: "allow" });

const journeyId = "11111111-1111-4111-8111-111111111111";
const contentId = "22222222-2222-4222-8222-222222222222";
const classroomId = "33333333-3333-4333-8333-333333333333";
const accountId = "44444444-4444-4444-8444-444444444444";
const secret = "segredo-offline-forte-2026";

function snapshot(completed = false) {
  return {
    schemaVersion: 1,
    journey: {
      id: journeyId, title: "Jornada SQL privada", description: "Material protegido",
      progress: { completedContents: completed ? 1 : 0, totalContents: 1, percent: completed ? 100 : 0 },
      releasedChapterCount: null, newlyUnlockedAchievements: [],
      modules: [{
        id: null, title: "Módulo SQL", description: null,
        contents: [{ id: contentId, title: "Conteúdo do aluno", description: null,
          state: completed ? "COMPLETED" : "CURRENT", resourceId: null, hasExternalSource: false }],
      }],
    },
    contents: [{
      id: contentId, title: "Conteúdo do aluno", description: null,
      state: completed ? "COMPLETED" : "CURRENT", resourceId: null,
      body: "# SQL privado\nSELECT nome FROM alunos;", format: "MARKDOWN",
      sourceUrl: null, sourceLabel: null, copyProtected: true,
    }],
    updatedAt: "2026-11-18T00:00:00Z",
    expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
  };
}

async function mockAccount(page: import("@playwright/test").Page,
                           state: { completed: boolean; downloads: number; writes: number; enabled: boolean }) {
  await page.route("**/api/student-account/session", (route) =>
    route.fulfill({ status: 200, contentType: "application/json",
      body: JSON.stringify({ accountId, studentId: "55555555-5555-4555-8555-555555555555", role: "STUDENT" }) }));
  await page.route("**/api/student-account/study/journeys", (route) =>
    route.fulfill({ status: 200, contentType: "application/json",
      body: JSON.stringify([{ id: journeyId, title: "Jornada SQL privada", description: null,
        progress: { completedContents: 0, totalContents: 1, percent: 0 },
        currentContentId: contentId, currentContentTitle: "Conteúdo do aluno", started: false,
        lastStudiedAt: null, classroomId, classroomName: "Turma SQL", classroomCode: "SQL",
        offlineAllowed: state.enabled }]) }));
  await page.route("**/api/student-account/study/offline/capabilities", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: '{"enabled":true}' }));
  await page.route("**/api/student-account/study/offline/journeys/" + journeyId, (route) => {
    state.downloads += 1;
    return route.fulfill({ status: state.enabled ? 200 : 403,
      contentType: "application/json", body: JSON.stringify(snapshot(state.completed)) });
  });
  await page.route("**/api/auth/csrf", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: '{"token":"csrf-offline"}' }));
  await page.route("**/api/student-account/study/journeys/" + journeyId + "/contents/" + contentId + "/complete", (route) => {
    expect(route.request().method()).toBe("POST");
    expect(route.request().headers()["x-xsrf-token"]).toBe("csrf-offline");
    expect(route.request().headers()["x-study-token"]).toBeUndefined();
    state.completed = true; state.writes += 1;
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(snapshot(true).journey) });
  });
}

test("download autorizado, leitura offline criptografada e sincronização explícita", async ({ page, context, browserName }) => {
  test.skip(browserName === "webkit", "WebKit Service Worker bypasses Playwright API mocks; separate real offline WebKit test below.");
  const state = { completed: false, downloads: 0, writes: 0, enabled: true };
  await mockAccount(page, state);
  await page.goto("/learn/journeys");
  await expect(page.getByText("Jornada SQL privada")).toBeVisible();
  await expect(page.getByRole("link", { name: "Disponibilizar offline" })).toBeVisible();

  // The worker must cache the entire standalone reader before going offline.
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
    if (!navigator.serviceWorker.controller) await new Promise<void>((resolve) =>
      navigator.serviceWorker.addEventListener("controllerchange", () => resolve(), { once: true }));
  });
  const offlineShellReady = await page.evaluate(async () => {
    const cacheKeys = await caches.keys();
    for (const key of cacheKeys) {
      const cache = await caches.open(key);
      if (await cache.match("/offline-journeys.html")
        && await cache.match("/offline-journeys.js")
        && await cache.match("/offline-journeys.css")) return true;
    }
    return false;
  });
  expect(offlineShellReady).toBe(true);
  await page.getByRole("link", { name: "Disponibilizar offline" }).click();
  await expect(page.getByRole("heading", { name: "Disponibilizar Jornada offline" })).toBeVisible();
  await page.locator("#download-secret").fill(secret);
  await page.locator("#download-confirm").fill(secret);
  await page.getByRole("button", { name: "Baixar conteúdo permitido" }).click();
  await expect(page.getByText(/Jornada baixada e criptografada/)).toBeVisible();
  expect(state.downloads).toBe(1);

  // The database must contain ciphertext, not the readable lesson or token.
  const noClearText = await page.evaluate(async (expectedAccountId) => {
    const request = indexedDB.open("ramuvi-offline-v1");
    const db = await new Promise<IDBDatabase>((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    const value = await new Promise<unknown>((resolve, reject) => {
      const tx = db.transaction("journeys", "readonly");
      const result = tx.objectStore("journeys").getAll();
      result.onsuccess = () => resolve(result.result);
      result.onerror = () => reject(result.error);
    });
    db.close();
    return !JSON.stringify(value).includes("SQL privado")
      && !JSON.stringify(value).includes("SELECT nome")
      && !JSON.stringify(value).includes(expectedAccountId);
  }, accountId);
  expect(noClearText).toBe(true);
  await context.setOffline(true);
  await page.goto("/offline-journeys.html");
  await expect(page.getByRole("heading", { name: "Suas Jornadas, mesmo sem conexão" })).toBeVisible();
  await page.getByRole("button", { name: "Desbloquear" }).click();
  await page.locator("#unlock-secret").fill("senha-errada");
  await page.getByRole("button", { name: "Abrir minha Jornada" }).click();
  await expect(page.getByText(/Senha incorreta/)).toBeVisible();
  await page.locator("#unlock-secret").fill(secret);
  await page.getByRole("button", { name: "Abrir minha Jornada" }).click();
  await expect(page.getByRole("heading", { name: "Jornada SQL privada" })).toBeVisible();
  await expect(page.getByText("SELECT nome FROM alunos;")).toBeVisible();
  await page.getByRole("button", { name: "Concluir neste dispositivo" }).click();
  await expect(page.getByText(/1 conclusão\(ões\) aguardando sincronização/)).toBeVisible();
  expect(state.writes).toBe(0);
  await context.setOffline(false);
  await page.getByRole("button", { name: "Sincronizar progresso e verificar atualizações" }).click();
  await expect(page.getByText(/Progresso sincronizado e pacote atualizado/)).toBeVisible();
  await expect(page.getByText("Nenhuma conclusão pendente.")).toBeVisible();
  expect(state.writes).toBe(1);
});

test("professor que desabilita o download não disponibiliza a ação", async ({ page }) => {
  const state = { completed: false, downloads: 0, writes: 0, enabled: false };
  await mockAccount(page, state);
  await page.goto("/learn/journeys");
  await expect(page.getByText("Jornada SQL privada")).toBeVisible();
  await expect(page.getByRole("link", { name: "Disponibilizar offline" })).toHaveCount(0);
  expect(state.downloads).toBe(0);
});

test("o Service Worker nunca armazena resposta acadêmica em Cache API", async ({ page }) => {
  await page.goto("/learn/journeys");
  await page.evaluate(() => navigator.serviceWorker.ready);
  const domainCached = await page.evaluate(async () => {
    const names = await caches.keys();
    for (const name of names) {
      const cache = await caches.open(name);
      if (await cache.match("/api/student-account/study/offline/journeys/" + "11111111-1111-4111-8111-111111111111")) return true;
    }
    return false;
  });
  expect(domainCached).toBe(false);
});


test("WebKit: página e conteúdo criptografado são legíveis no modo avião e progresso persiste", async ({ page, context, browserName }) => {
  test.skip(browserName !== "webkit", "Safari engine coverage; Chromium exercises the backend download and sync.");
  await page.goto("/offline-journeys.html");
  // Seed a previously authorized and encrypted package without API interception.
  // This isolates the actual WebKit Service Worker, IndexedDB and WebCrypto.
  await page.evaluate(async (input) => {
    const encoder = new TextEncoder();
    const hash = new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(input.account)));
    const localKey = [...hash].map((part) => part.toString(16).padStart(2, "0")).join("") + ":" + input.snapshot.journey.id;
    const packet = { schemaVersion: 1, localKey, ownerAccountId: input.account, snapshot: input.snapshot, pending: [] };
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const imported = await crypto.subtle.importKey("raw", encoder.encode(input.secret), "PBKDF2", false, ["deriveKey"]);
    const key = await crypto.subtle.deriveKey({ name: "PBKDF2", salt, iterations: 250000, hash: "SHA-256" },
      imported, { name: "AES-GCM", length: 256 }, false, ["encrypt"]);
    const payload = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv },
      key, encoder.encode(JSON.stringify(packet))));
    await new Promise<void>((resolve, reject) => {
      const req = indexedDB.open("ramuvi-offline-v1", 1);
      req.onupgradeneeded = () => {
        if (!req.result.objectStoreNames.contains("journeys"))
          req.result.createObjectStore("journeys", { keyPath: "id" });
      };
      req.onerror = () => reject(req.error);
      req.onsuccess = () => {
        const db = req.result;
        const tx = db.transaction("journeys", "readwrite");
        tx.objectStore("journeys").put({ id: localKey, salt, iv, payload,
          expiresAt: input.snapshot.expiresAt, savedAt: new Date().toISOString() });
        tx.oncomplete = () => { db.close(); resolve(); };
        tx.onerror = () => { db.close(); reject(tx.error); };
      };
    });
    await navigator.serviceWorker.register("/sw.js", { scope: "/" });
    await navigator.serviceWorker.ready;
  }, { account: accountId, snapshot: snapshot(false), secret });
  await page.reload();
  await expect(page.getByRole("button", { name: "Desbloquear" })).toBeVisible();
  const controlledAndCached = await page.evaluate(async () => {
    const controlled = Boolean(navigator.serviceWorker.controller);
    const names = await caches.keys();
    let cached = false;
    for (const name of names) {
      const store = await caches.open(name);
      if (await store.match("/offline-journeys.html")
        && await store.match("/offline-journeys.js")
        && await store.match("/offline-journeys.css")) cached = true;
    }
    return { controlled, cached };
  });
  expect(controlledAndCached).toEqual({ controlled: true, cached: true });
  await context.setOffline(true);
  // WebKit Playwright reports "internal error" on offline cold navigation
  // and "Load failed" on fetch() despite an active controller. Validate local
  // cached assets and offline IndexedDB/WebCrypto separately. Real iPhone
  // cold-launch remains an explicit unpassed release gate.
  const cacheIsLocal = await page.evaluate(async () => {
    const keys = await caches.keys();
    for (const key of keys) {
      const cache = await caches.open(key);
      if (await cache.match("/offline-journeys.html")
        && await cache.match("/offline-journeys.js")
        && await cache.match("/offline-journeys.css")) return true;
    }
    return false;
  });
  expect(cacheIsLocal).toBe(true);
  await page.getByRole("button", { name: "Desbloquear" }).click();
  await page.locator("#unlock-secret").fill(secret);
  await page.getByRole("button", { name: "Abrir minha Jornada" }).click();
  await expect(page.getByRole("heading", { name: "Jornada SQL privada" })).toBeVisible();
  await expect(page.getByText("SELECT nome FROM alunos;")).toBeVisible();
  await page.getByRole("button", { name: "Concluir neste dispositivo" }).click();
  await expect(page.getByText(/1 conclusão\(ões\) aguardando sincronização/)).toBeVisible();
  // Online reload validates durable persistence after marking completed offline.
  await context.setOffline(false);
  await page.reload();
  await page.getByRole("button", { name: "Desbloquear" }).click();
  await page.locator("#unlock-secret").fill(secret);
  await page.getByRole("button", { name: "Abrir minha Jornada" }).click();
  await expect(page.getByText(/1 conclusão\(ões\) aguardando sincronização/)).toBeVisible();
});


test("permissão revogada remove o pacote na próxima sincronização autenticada", async ({ page, browserName }) => {
  test.skip(browserName === "webkit", "API mocks under a controlling WebKit SW are not intercepted in this test runner.");
  const state = { completed: false, downloads: 0, writes: 0, enabled: true };
  await mockAccount(page, state);
  await page.goto("/offline-journeys.html?journey=" + journeyId);
  await page.locator("#download-secret").fill(secret);
  await page.locator("#download-confirm").fill(secret);
  await page.getByRole("button", { name: "Baixar conteúdo permitido" }).click();
  await expect(page.getByText(/Jornada baixada e criptografada/)).toBeVisible();
  await page.getByRole("button", { name: "Desbloquear" }).click();
  await page.locator("#unlock-secret").fill(secret);
  await page.getByRole("button", { name: "Abrir minha Jornada" }).click();
  state.enabled = false;
  await page.getByRole("button", { name: "Sincronizar progresso e verificar atualizações" }).click();
  await expect(page.getByText(/Acesso offline revogado. O pacote foi removido/)).toBeVisible();
  await expect(page.getByText("Nenhuma Jornada foi baixada neste dispositivo.")).toBeVisible();
  expect(state.writes).toBe(0);
});

test("duas contas guardam separadamente a mesma Jornada no dispositivo", async ({ page, browserName }) => {
  test.skip(browserName === "webkit", "API mocks under a controlling WebKit SW are not intercepted in this test runner.");
  let userId = accountId;
  await page.route("**/api/student-account/session", (route) =>
    route.fulfill({ status: 200, contentType: "application/json",
      body: JSON.stringify({ accountId: userId, studentId: "99999999-9999-4999-8999-999999999999", role: "STUDENT" }) }));
  await page.route("**/api/student-account/study/offline/capabilities", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: '{"enabled":true}' }));
  await page.route("**/api/student-account/study/offline/journeys/" + journeyId, (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(snapshot(false)) }));
  await page.goto("/offline-journeys.html?journey=" + journeyId);
  await page.locator("#download-secret").fill(secret);
  await page.locator("#download-confirm").fill(secret);
  await page.getByRole("button", { name: "Baixar conteúdo permitido" }).click();
  await expect(page.getByText(/Jornada baixada e criptografada/)).toBeVisible();
  userId = "88888888-8888-4888-8888-888888888888";
  await page.locator("#download-secret").fill("senha-distinta-segura-2026");
  await page.locator("#download-confirm").fill("senha-distinta-segura-2026");
  await page.getByRole("button", { name: "Baixar conteúdo permitido" }).click();
  await expect(page.locator("#saved-list .stored")).toHaveCount(2);
  await page.locator("#saved-list .stored").first().getByRole("button", { name: "Desbloquear" }).click();
  await page.locator("#unlock-secret").fill(secret);
  await page.getByRole("button", { name: "Abrir minha Jornada" }).click();
  await expect(page.getByRole("heading", { name: "Jornada SQL privada" })).toBeVisible();
  await page.getByRole("button", { name: "Bloquear", exact: true }).click();
  await page.locator("#saved-list .stored").last().getByRole("button", { name: "Desbloquear" }).click();
  await page.locator("#unlock-secret").fill("senha-distinta-segura-2026");
  await page.getByRole("button", { name: "Abrir minha Jornada" }).click();
  await expect(page.getByRole("heading", { name: "Jornada SQL privada" })).toBeVisible();
});
