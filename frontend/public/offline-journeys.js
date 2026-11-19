// Ramuvi: static reader. SW caches only this shell; encrypted packages live in IndexedDB.
const el = (id) => document.getElementById(id);
const enc = new TextEncoder(), dec = new TextDecoder();
const DB_NAME = "ramuvi-offline-v1", STORE = "journeys";
async function packageKey(accountId, journeyId) {
  // Opaque per-account namespace prevents different students on one device
  // from overwriting each other's encrypted copy of the same Journey.
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", enc.encode(accountId)));
  return [...digest].map((byte) => byte.toString(16).padStart(2, "0")).join("") + ":" + journeyId;
}
let active = null, selectedId = null;
const show = (id, yes) => { el(id).hidden = !yes; };
function say(message, error = false) {
  el("status").textContent = message;
  el("status").classList.toggle("error", error);
}
function elem(tag, message, className) {
  const item = document.createElement(tag);
  if (message !== undefined) item.textContent = message;
  if (className) item.className = className;
  return item;
}
function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains(STORE)) req.result.createObjectStore(STORE, { keyPath: "id" });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error || new Error("Armazenamento indisponível."));
    req.onblocked = () => reject(new Error("Feche outras abas da Ramuvi antes de continuar."));
  });
}
async function dbAction(action, id, value) {
  const db = await openDb();
  try {
    return await new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE, action === "all" || action === "get" ? "readonly" : "readwrite");
      const store = transaction.objectStore(STORE);
      const request = action === "all" ? store.getAll()
        : action === "get" ? store.get(id)
        : action === "put" ? store.put(value) : store.delete(id);
      transaction.oncomplete = () => resolve(request.result);
      transaction.onerror = () => reject(transaction.error || new Error("Falha no armazenamento local."));
      transaction.onabort = () => reject(transaction.error || new Error("Operação local cancelada."));
    });
  } finally { db.close(); }
}
function bytes(n) { return crypto.getRandomValues(new Uint8Array(n)); }
async function keyFor(password, salt) {
  const base = await crypto.subtle.importKey("raw", enc.encode(password), "PBKDF2", false, ["deriveKey"]);
  return crypto.subtle.deriveKey({ name: "PBKDF2", salt, iterations: 250000, hash: "SHA-256" }, base,
    { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
}
async function encrypt(packet, key, salt) {
  const iv = bytes(12);
  const payload = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key,
    enc.encode(JSON.stringify(packet))));
  return { id: packet.localKey, salt, iv, payload,
    expiresAt: packet.snapshot.expiresAt, savedAt: new Date().toISOString() };
}
async function decrypt(row, password) {
  const key = await keyFor(password, row.salt);
  try {
    const data = await crypto.subtle.decrypt({ name: "AES-GCM", iv: row.iv }, key, row.payload);
    const packet = JSON.parse(dec.decode(data));
    if (packet.schemaVersion !== 1 || packet.snapshot?.schemaVersion !== 1
      || packet.localKey !== row.id || !Array.isArray(packet.pending)) {
      throw new Error("Versão de pacote incompatível.");
    }
    return { packet, key };
  } catch { throw new Error("Senha incorreta ou pacote local danificado."); }
}
async function saveActive() {
  active.row = await encrypt(active.packet, active.key, active.row.salt);
  await dbAction("put", active.row.id, active.row);
}
function expired(row) { return Date.parse(row.expiresAt) <= Date.now(); }
async function fetchJson(path) {
  const response = await fetch(path, { cache: "no-store", credentials: "include" });
  if (!response.ok) {
    const error = new Error(response.status === 401 ? "Entre novamente na sua conta."
      : response.status === 403 || response.status === 404 ? "Acesso offline não permitido para esta Jornada."
      : response.status === 409 ? "Não há material liberado para download."
      : "Falha ao consultar a Ramuvi. Confira a conexão.");
    error.status = response.status;
    throw error;
  }
  return response.json();
}
async function currentAccount() {
  const session = await fetchJson("/api/student-account/session");
  if (!session.accountId || !session.studentId) throw new Error("Vincule sua matrícula antes de baixar.");
  return session;
}
async function refreshList() {
  const rows = (await dbAction("all")).sort((left, right) =>
    Date.parse(left.savedAt) - Date.parse(right.savedAt));
  const list = el("saved-list");
  list.replaceChildren();
  if (!rows.length) { list.append(elem("p", "Nenhuma Jornada foi baixada neste dispositivo.", "muted")); return; }
  for (const [i, row] of rows.entries()) {
    const block = elem("div", undefined, "stored");
    const info = elem("div");
    info.append(elem("strong", "Pacote offline " + (i + 1)),
      elem("small", expired(row) ? "Validade expirada" :
        "Validade: " + new Date(row.expiresAt).toLocaleDateString("pt-BR")));
    const controls = elem("div");
    const open = elem("button", "Desbloquear");
    open.type = "button";
    open.addEventListener("click", () => {
      selectedId = row.id; show("unlock", true);
      el("selected-package").textContent = "Pacote " + (i + 1);
      el("unlock-secret").value = "";
      el("unlock-secret").focus();
    });
    const remove = elem("button", "Remover", "secondary");
    remove.type = "button";
    remove.addEventListener("click", async () => {
      if (!confirm("Excluir o pacote e todas as conclusões pendentes neste dispositivo?")) return;
      try {
        await dbAction("delete", row.id);
        if (active?.row.id === row.id) lockReader();
        await refreshList();
        say("Pacote removido.");
      } catch (error) { say(error.message, true); }
    });
    controls.append(open, remove); block.append(info, controls); list.append(block);
  }
}
function lockReader() {
  active = null; show("reader", false); el("contents").replaceChildren();
  say("Leitura bloqueada. Informe a senha offline para abrir novamente.");
}
function renderText(content, root) {
  // No innerHTML: teacher-provided HTML/Markdown never executes in the offline reader.
  let inCode = false;
  for (const line of String(content || "").split(/\r?\n/)) {
    if (line.trim().startsWith(String.fromCharCode(96).repeat(3))) { inCode = !inCode; continue; }
    if (inCode) root.append(elem("pre", line, "code"));
    else if (/^#{1,4}\s+/.test(line)) root.append(elem("h4", line.replace(/^#{1,4}\s+/, "")));
    else root.append(elem("div", line || " ", "body"));
  }
}
function renderReader() {
  if (!active) return;
  const { row, packet } = active, snapshot = packet.snapshot;
  el("journey-title").textContent = snapshot.journey.title;
  el("expires").textContent = "Válido até " + new Date(row.expiresAt).toLocaleString("pt-BR")
    + ". Permissões são conferidas ao reconectar.";
  el("pending").textContent = packet.pending.length
    ? packet.pending.length + " conclusão(ões) aguardando sincronização."
    : "Nenhuma conclusão pendente.";
  const root = el("contents");
  root.replaceChildren();
  if (expired(row)) {
    root.append(elem("p", "O pacote expirou. Reconecte-se e atualize antes de continuar a leitura.", "warn"));
    return;
  }
  const downloaded = new Map(snapshot.contents.map((content) => [content.id, content]));
  for (const chapter of snapshot.journey.modules) {
    const stages = chapter.contents.filter((stage) => downloaded.has(stage.id));
    if (!stages.length) continue;
    root.append(elem("h3", chapter.title || "Conteúdo"));
    for (const stage of stages) {
      const content = downloaded.get(stage.id);
      const item = elem("article", undefined, "material");
      item.append(elem("h3", content.title));
      if (content.copyProtected) {
        item.append(elem("p", "Cópia desativada pelo professor. Não constitui proteção DRM.", "muted"));
        for (const action of ["copy", "cut", "contextmenu"]) item.addEventListener(action, (event) => event.preventDefault());
        item.style.userSelect = "none";
      }
      renderText(content.body, item);
      if (content.sourceUrl) item.append(elem("p", "Recurso externo complementar requer conexão.", "muted"));
      const locallyDone = packet.pending.includes(stage.id);
      const completed = content.state === "COMPLETED" || locallyDone;
      item.append(elem("p", locallyDone ? "Conclusão pendente de sincronização."
        : completed ? "Concluído no servidor." : "Pronto para estudar.", "state"));
      if (!completed) {
        const finish = elem("button", "Concluir neste dispositivo");
        finish.type = "button";
        finish.addEventListener("click", async () => {
          if (!active || active.packet.pending.includes(stage.id)) return;
          active.packet.pending.push(stage.id);
          try {
            await saveActive();
            renderReader(); await refreshList();
            say("Progresso salvo neste dispositivo. Sincronize quando estiver conectado.");
          } catch (error) {
            active.packet.pending = active.packet.pending.filter((id) => id !== stage.id);
            say("Erro ao salvar localmente: " + error.message, true);
          }
        });
        item.append(finish);
      }
      root.append(item);
    }
  }
}

el("download-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const secret = el("download-secret").value;
  const confirmation = el("download-confirm").value;
  if (secret.length < 12 || secret !== confirmation) {
    return say("Use pelo menos 12 caracteres e confirme a mesma senha offline.", true);
  }
  const id = new URLSearchParams(location.search).get("journey");
  if (!id || !/^[a-f\d-]{36}$/i.test(id)) return say("Identificador da Jornada inválido.", true);
  const button = el("download-button"); button.disabled = true;
  try {
    const session = await currentAccount();
    await fetchJson("/api/student-account/study/offline/capabilities");
    const snapshot = await fetchJson("/api/student-account/study/offline/journeys/" + encodeURIComponent(id));
    if (snapshot.schemaVersion !== 1 || snapshot.journey.id !== id || !snapshot.contents?.length)
      throw new Error("Pacote incompleto ou incompatível.");
    const localKey = await packageKey(session.accountId, id);
    const older = await dbAction("get", localKey);
    if (older) {
      const previous = await decrypt(older, secret);
      if (previous.packet.ownerAccountId !== session.accountId)
        throw new Error("Há um pacote associado a outra conta. Remova-o antes de baixar.");
      if (previous.packet.pending.length)
        throw new Error("Sincronize o progresso pendente antes de atualizar este pacote.");
    }
    const packet = { schemaVersion: 1, localKey, ownerAccountId: session.accountId, snapshot, pending: [] };
    const salt = bytes(16);
    const key = await keyFor(secret, salt);
    const row = await encrypt(packet, key, salt);
    await dbAction("put", row.id, row);
    el("download-secret").value = ""; el("download-confirm").value = "";
    await refreshList();
    say("Jornada baixada e criptografada. Pode ser aberta sem internet neste navegador.");
  } catch (error) { say(error.message || "Download não concluído.", true); }
  finally { button.disabled = false; }
});

el("unlock-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const row = await dbAction("get", selectedId);
    if (!row) throw new Error("Pacote não encontrado.");
    const { key, packet } = await decrypt(row, el("unlock-secret").value);
    active = { row, key, packet };
    el("unlock-secret").value = "";
    show("unlock", false); show("reader", true);
    renderReader();
    say(expired(row) ? "A leitura expirou; reconecte-se para atualizar." : "Pacote desbloqueado.");
  } catch (error) { say(error.message || "Falha ao abrir pacote.", true); }
});
el("lock").addEventListener("click", lockReader);
el("refresh").addEventListener("click", () => void refreshList().catch((error) => say(error.message, true)));

el("sync").addEventListener("click", async () => {
  if (!active) return;
  const button = el("sync"); button.disabled = true;
  try {
    const session = await currentAccount();
    if (session.accountId !== active.packet.ownerAccountId) {
      throw new Error("Conecte-se com a mesma conta que criou este download.");
    }
    const id = active.packet.snapshot.journey.id;
    let latest;
    try {
      latest = await fetchJson("/api/student-account/study/offline/journeys/" + encodeURIComponent(id));
    } catch (error) {
      if (error.status === 403 || error.status === 404) {
        await dbAction("delete", active.row.id);
        lockReader();
        await refreshList();
        throw new Error("Acesso offline revogado. O pacote foi removido deste dispositivo.");
      }
      throw error;
    }
    const available = new Set(latest.contents.map((content) => content.id));
    for (const contentId of [...active.packet.pending]) {
      if (!available.has(contentId)) {
        throw new Error("Um conteúdo concluído não está mais liberado. Peça orientação ao professor.");
      }
      const csrfResponse = await fetch("/api/auth/csrf", { credentials: "include", cache: "no-store" });
      if (!csrfResponse.ok) throw new Error("Sua sessão expirou. Entre novamente para sincronizar.");
      const csrf = await csrfResponse.json();
      const response = await fetch("/api/student-account/study/journeys/"
        + encodeURIComponent(id) + "/contents/" + encodeURIComponent(contentId) + "/complete", {
        method: "POST", credentials: "include", cache: "no-store",
        headers: { "X-XSRF-TOKEN": csrf.token, "Content-Type": "application/json" },
        body: "{}",
      });
      if (!response.ok) {
        throw new Error("Conclusão rejeitada pelo servidor (" + response.status
          + "). O progresso continua salvo localmente.");
      }
      active.packet.pending = active.packet.pending.filter((item) => item !== contentId);
      // Save each acknowledged completion before attempting the next request.
      await saveActive();
    }
    // A fresh manifest may unlock another chapter or remove previously offered content.
    latest = await fetchJson("/api/student-account/study/offline/journeys/" + encodeURIComponent(id));
    active.packet.snapshot = latest;
    await saveActive();
    await refreshList();
    renderReader();
    say("Progresso sincronizado e pacote atualizado com as permissões atuais.");
  } catch (error) {
    if (active) renderReader();
    say(error.message || "Não foi possível sincronizar.", true);
  } finally { button.disabled = false; }
});
(async () => {
  if (new URLSearchParams(location.search).has("journey")) show("download", true);
  if (!("indexedDB" in window) || !crypto?.subtle) {
    say("Seu navegador não oferece armazenamento criptografado compatível.", true);
    show("download", false);
    return;
  }
  try { await refreshList(); }
  catch (error) { say("Não foi possível ler pacotes locais: " + error.message, true); }
})();
