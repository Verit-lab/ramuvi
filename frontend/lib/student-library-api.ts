import { apiFetch } from "./auth-api";

// v0.12.2 I3a/I3b (ADR-0076 D7/D8): public preview of a shared material and Minha Biblioteca.
// The share token travels in the URL FRAGMENT (#acesso=...), never reaches servers, logs or
// Referer; it is moved to this tab's sessionStorage, removed from the address bar and sent only
// in POST bodies.

export type LibraryFormat = "MARKDOWN" | "PLAIN_TEXT" | "LINK";

export type SharedMaterial = {
  title: string;
  summary: string | null;
  format: LibraryFormat;
  body: string | null;
  sourceUrl: string | null;
  sourceLabel: string | null;
  revision: number;
  copyProtected: boolean;
  externalReference: boolean;
  permission: "VIEW" | "SAVE";
  expiresAt: string;
  canSave: boolean;
  alreadySaved: boolean;
  savedVersionDiffers: boolean;
};

export type SaveResult = { itemId: string; status: "SAVED" | "ALREADY_SAVED" | "UPDATED"; newerVersionAvailable: boolean };

export type LibraryItemSummary = {
  id: string;
  title: string;
  summary: string | null;
  format: LibraryFormat;
  revision: number;
  externalReference: boolean;
  savedAt: string;
  updatedAt: string;
};

export type LibraryItemContent = {
  id: string;
  title: string;
  summary: string | null;
  format: LibraryFormat;
  body: string | null;
  sourceUrl: string | null;
  sourceLabel: string | null;
  revision: number;
  copyProtected: boolean;
  externalReference: boolean;
  savedAt: string;
};

export type LibraryErrorKind = "UNAVAILABLE" | "ACCOUNT_REQUIRED" | "VIEW_ONLY" | "RATE_LIMITED" | "INVALID" | "NETWORK";

export class LibraryAccessError extends Error {
  constructor(message: string, public readonly kind: LibraryErrorKind, public readonly status: number) {
    super(message);
    this.name = "LibraryAccessError";
  }
}

const PENDING_KEY = "ramuvi:library-share";
const TOKEN = /^[A-Z2-7]{32}$/;

export function normalizeShareToken(raw: string | null | undefined): string | null {
  if (!raw || raw.length > 64) return null;
  const compact = raw.replace(/[\s-]/g, "").toUpperCase();
  return TOKEN.test(compact) ? compact : null;
}

/** Moves #acesso=TOKEN to this tab only and clears it from the address bar (and history). */
export function captureShareTokenFromLocation(): string | null {
  if (typeof window === "undefined") return null;
  const hash = window.location.hash.replace(/^#/, "");
  const raw = new URLSearchParams(hash).get("acesso");
  if (raw !== null) {
    window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}`);
    const token = normalizeShareToken(raw);
    if (token) window.sessionStorage.setItem(PENDING_KEY, token);
    return token;
  }
  return pendingShareToken();
}

export function pendingShareToken(): string | null {
  if (typeof window === "undefined") return null;
  return normalizeShareToken(window.sessionStorage.getItem(PENDING_KEY));
}

export function clearPendingShareToken(): void {
  if (typeof window !== "undefined") window.sessionStorage.removeItem(PENDING_KEY);
}

async function errorOf(response: Response): Promise<LibraryAccessError> {
  const body = (await response.json().catch(() => ({}))) as { message?: string; code?: string };
  if (response.status === 401) {
    return new LibraryAccessError(body.message ?? "Entre com sua conta de estudante para continuar.", "ACCOUNT_REQUIRED", 401);
  }
  if (response.status === 403 && body.code === "VIEW_ONLY") {
    return new LibraryAccessError(body.message ?? "Este link permite apenas visualizar o material.", "VIEW_ONLY", 403);
  }
  if (response.status === 404) {
    return new LibraryAccessError(body.message ?? "Este link de material não está disponível.", "UNAVAILABLE", 404);
  }
  if (response.status === 429) {
    return new LibraryAccessError("Muitas tentativas. Aguarde um minuto e tente novamente.", "RATE_LIMITED", 429);
  }
  if (response.status === 400) {
    return new LibraryAccessError("O link de material está incompleto ou inválido.", "INVALID", 400);
  }
  return new LibraryAccessError("Não foi possível abrir o material. Tente novamente.", "NETWORK", response.status);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await apiFetch(path, init);
  } catch {
    throw new LibraryAccessError("Sem conexão. Verifique a internet e tente novamente.", "NETWORK", 0);
  }
  if (!response.ok) throw await errorOf(response);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function previewSharedMaterial(token: string) {
  return request<SharedMaterial>("/api/library/shares/preview", { method: "POST", body: JSON.stringify({ token }) });
}

export function saveSharedMaterial(token: string, replaceVersion = false) {
  return request<SaveResult>("/api/student-account/library/items", {
    method: "POST",
    body: JSON.stringify({ token, replaceVersion }),
  });
}

export function fetchMyLibrary() {
  return request<LibraryItemSummary[]>("/api/student-account/library/items");
}

export function fetchMyLibraryItem(itemId: string) {
  return request<LibraryItemContent>(`/api/student-account/library/items/${encodeURIComponent(itemId)}`);
}

export function removeMyLibraryItem(itemId: string) {
  return request<void>(`/api/student-account/library/items/${encodeURIComponent(itemId)}`, { method: "DELETE" });
}

export function saveResultMessage(result: SaveResult): string {
  if (result.status === "SAVED") return "Material salvo na sua Biblioteca.";
  if (result.status === "UPDATED") return "Sua Biblioteca agora guarda esta versão do material.";
  return result.newerVersionAvailable
    ? "Você já salvou outra versão deste material. A versão salva continua a mesma."
    : "Este material já está na sua Biblioteca.";
}

export function formatLabel(format: LibraryFormat): string {
  return format === "LINK" ? "Link externo" : format === "PLAIN_TEXT" ? "Texto" : "Texto formatado";
}
