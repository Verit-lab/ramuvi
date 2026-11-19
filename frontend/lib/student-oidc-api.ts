import { apiFetch } from "./auth-api";

// A non-secret browser hint prevents an expired or revoked verified session from
// silently reusing a different student's remembered device claim.
// This is a UI boundary, NOT proof of identity or an authorization credential.
const observedAccountKey = "ramuvi:student-account-observed";
// Distinguish intentional logout from a revoked/expired account without removing
// the verified-identity boundary that blocks implicit device-token fallback.
const signedOutAccountKey = "ramuvi:student-account-signed-out";

export function studentAccountWasSignedOut(): boolean {
  try { return window.localStorage.getItem(signedOutAccountKey) === "1"; }
  catch { return false; }
}

function accountWasObserved(): boolean {
  try { return window.localStorage.getItem(observedAccountKey) === "1"; }
  catch { return false; }
}

function markAccountObserved(): void {
  try {
    window.localStorage.setItem(observedAccountKey, "1");
    window.localStorage.removeItem(signedOutAccountKey);
  } catch { /* storage may be restricted; backend authorization remains authoritative */ }
}

export function leaveStudentAccountMode(): void {
  try {
    window.localStorage.removeItem(observedAccountKey);
    window.localStorage.removeItem(signedOutAccountKey);
  } catch { /* browser storage may be restricted */ }
}

export class StudentAccountSessionUnavailable extends Error {
  constructor() {
    super("Sua sessão de conta foi encerrada ou revogada. Entre novamente ou escolha explicitamente o acesso por código.");
    this.name = "StudentAccountSessionUnavailable";
  }
}

export type StudentOidcProvider = {
  id: "google" | "microsoft";
  name: string;
  path: string;
};

export type StudentOidcSession = {
  accountId: string;
  studentId: string | null;
  role: "STUDENT";
};

export async function fetchStudentOidcProviders(): Promise<StudentOidcProvider[]> {
  const response = await apiFetch("/api/student-account/providers");
  if (response.status === 404) return []; // Off by default until configured.
  if (!response.ok) throw new Error("Não foi possível consultar os provedores de acesso.");
  const values: unknown = await response.json();
  if (!Array.isArray(values)) throw new Error("Resposta de provedores inválida.");
  return values.filter((item): item is StudentOidcProvider =>
    item != null && typeof item === "object" &&
    ("id" in item) && (item.id === "google" || item.id === "microsoft") &&
    ("name" in item) && typeof item.name === "string" &&
    ("path" in item) && typeof item.path === "string" &&
    item.path === `/api/student-oidc/authorization/${item.id}`
  );
}

export async function fetchStudentOidcSession(): Promise<StudentOidcSession | null> {
  const response = await apiFetch("/api/student-account/session");
  // 404: OIDC is disabled and the existing code-based learner mode remains available.
  if (response.status === 404) return null;
  if (response.status === 401) {
    if (accountWasObserved()) throw new StudentAccountSessionUnavailable();
    return null; // never signed in here, so guest mode remains intentional
  }
  if (response.status === 403) throw new StudentAccountSessionUnavailable();
  if (!response.ok) throw new Error("Não foi possível consultar sua sessão.");
  const session = (await response.json()) as StudentOidcSession;
  markAccountObserved();
  return session;
}


export type StudentActiveClassroom = {
  enrollmentId: string;
  classroomId: string;
  classroomName: string;
  studentStudyAccessEnabled: boolean;
};

export type ClaimedEnrollment = {
  studentId: string;
  enrollmentId: string;
  classroomId: string;
  classroomName: string;
};

/** Returns null when the I2 rollout switch is OFF. */
export async function fetchStudentClassrooms(): Promise<StudentActiveClassroom[] | null> {
  const response = await apiFetch("/api/student-account/classrooms");
  if (response.status === 404) return null;
  if (!response.ok) throw new Error("Não foi possível consultar as turmas desta conta.");
  return (await response.json()) as StudentActiveClassroom[];
}

export async function claimStudentEnrollment(token: string): Promise<ClaimedEnrollment> {
  if (!/^[A-Za-z0-9_-]{43}$/.test(token)) {
    throw new Error("Digite o código individual completo fornecido pelo professor.");
  }
  const response = await apiFetch("/api/student-account/enrollments/claim", {
    method: "POST",
    body: JSON.stringify({ token }), // Never put invitation secrets in a URL.
  });
  if (!response.ok) {
    throw new Error(response.status === 404
      ? "O vínculo por convite ainda não foi habilitado."
      : response.status === 401 || response.status === 403
        ? "Sua sessão expirou ou não possui permissão. Entre novamente."
        : response.status === 429
          ? "Muitas tentativas de vínculo. Aguarde antes de tentar novamente."
          : "Convite inválido, expirado, revogado ou já utilizado. Solicite um novo ao professor.");
  }
  return (await response.json()) as ClaimedEnrollment;
}

// ---- ADR-0074: classroom join link (token in URL fragment, POST body only) ----

const pendingJoinLinkKey = "ramuvi:pending-join-link";
const joinLinkToken = /^[A-Z2-7]{32}$/;

/** Accepts the token as copied/typed: lowercase, spaces or hyphens between groups. */
export function normalizeJoinLinkToken(raw: string): string | null {
  const compact = raw.replace(/[\s-]/g, "").toUpperCase();
  return joinLinkToken.test(compact) ? compact : null;
}

/**
 * Moves "#ingresso=TOKEN" out of the address bar into sessionStorage (tab-scoped,
 * cleared on logout) so it survives the OIDC redirect without staying in history.
 */
export function captureJoinLinkFromLocation(): string | null {
  try {
    const hash = new URLSearchParams(window.location.hash.replace(/^#/, ""));
    const incoming = hash.get("ingresso");
    if (incoming !== null) {
      const token = normalizeJoinLinkToken(incoming);
      window.history.replaceState(null, "", window.location.pathname + window.location.search);
      if (token) window.sessionStorage.setItem(pendingJoinLinkKey, token);
    }
    return window.sessionStorage.getItem(pendingJoinLinkKey);
  } catch {
    return null;
  }
}

export function clearPendingJoinLink(): void {
  try { window.sessionStorage.removeItem(pendingJoinLinkKey); } catch { /* storage restricted */ }
}

export type ClassroomLinkPreview = {
  classroomName: string;
  expiresAt: string;
  requiresApproval: boolean;
  alreadyMember: boolean;
  pendingRequest: boolean;
};
export type ClassroomJoinStatus =
  | "JOINED_NEW_STUDENT" | "LINKED_EXISTING_STUDENT" | "JOINED" | "ALREADY_MEMBER" | "PENDING_APPROVAL";
export type ClassroomJoinResult = { status: ClassroomJoinStatus; classroomName: string; journeysAvailable: boolean };
export type OwnJoinRequest = {
  requestId: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  classroomName: string;
  createdAt: string;
  decidedAt: string | null;
};

/** Kind of failure, so the UI can offer a retry only when retrying makes sense. */
export class ClassroomLinkError extends Error {
  constructor(message: string, readonly retryable: boolean) {
    super(message);
    this.name = "ClassroomLinkError";
  }
}

async function linkFailure(response: Response): Promise<never> {
  let message = "";
  try {
    const body = (await response.json()) as { message?: unknown };
    if (typeof body.message === "string") message = body.message;
  } catch { /* generic below */ }
  if (response.status === 401 || response.status === 403) {
    throw new ClassroomLinkError("Sua sessão expirou. Entre novamente com sua conta.", false);
  }
  if (response.status === 404) {
    throw new ClassroomLinkError("O ingresso por link não está habilitado nesta instalação. Peça um convite individual ao professor.", false);
  }
  if (response.status === 429) {
    throw new ClassroomLinkError("Muitas tentativas. Aguarde um minuto e tente novamente.", true);
  }
  if (response.status >= 500) {
    throw new ClassroomLinkError("O servidor não respondeu como esperado. Tente novamente em instantes.", true);
  }
  throw new ClassroomLinkError(message || "Link de ingresso inválido, expirado ou revogado. Peça um novo link ao professor.", false);
}

async function linkPost(path: string, token: string): Promise<Response> {
  try {
    return await apiFetch(path, { method: "POST", body: JSON.stringify({ token }) });
  } catch {
    throw new ClassroomLinkError("Sem conexão com a Ramuvi. Verifique a internet e tente novamente.", true);
  }
}

export async function previewClassroomLink(token: string): Promise<ClassroomLinkPreview> {
  const response = await linkPost("/api/student-account/classroom-links/preview", token);
  if (!response.ok) return linkFailure(response);
  return (await response.json()) as ClassroomLinkPreview;
}

export async function acceptClassroomLink(token: string): Promise<ClassroomJoinResult> {
  const response = await linkPost("/api/student-account/classroom-links/accept", token);
  if (!response.ok) return linkFailure(response);
  return (await response.json()) as ClassroomJoinResult;
}

/** Informational only: any failure (feature OFF, offline) simply shows no requests. */
export async function fetchOwnJoinRequests(): Promise<OwnJoinRequest[]> {
  try {
    const response = await apiFetch("/api/student-account/classroom-links/requests");
    if (!response.ok) return [];
    const items: unknown = await response.json();
    return Array.isArray(items) ? (items as OwnJoinRequest[]) : [];
  } catch {
    return [];
  }
}

/** Honest copy: XP is recovered only when the teacher's record was proven (or approved). */
export function joinResultMessage(result: ClassroomJoinResult): string {
  const journeys = result.journeysAvailable
    ? "As Jornadas publicadas desta turma aparecem no seu espaço de estudo."
    : "As Jornadas aparecerão quando o professor liberar o acesso a estudos desta turma.";
  switch (result.status) {
    case "LINKED_EXISTING_STUDENT":
      return `Você entrou na turma ${result.classroomName} com o cadastro que o professor já tinha, incluindo pontuações e progresso. ${journeys}`;
    case "JOINED_NEW_STUDENT":
      return `Você entrou na turma ${result.classroomName} com um cadastro novo. Se você já participava antes com outro e-mail, avise o professor para unir o histórico. ${journeys}`;
    case "JOINED":
      return `Você entrou na turma ${result.classroomName}. ${journeys}`;
    case "ALREADY_MEMBER":
      return `Você já participa da turma ${result.classroomName}. ${journeys}`;
    case "PENDING_APPROVAL":
      return `Seu pedido para entrar na turma ${result.classroomName} foi enviado ao professor. Nenhum cadastro foi criado ou vinculado ainda; volte depois de ele aprovar.`;
  }
}

export async function logoutStudentOidc(): Promise<void> {
  const response = await apiFetch("/api/student-account/logout", { method: "POST" });
  if (!response.ok) throw new Error("Não foi possível sair desta conta.");
  clearPendingJoinLink(); // Shared device: never leave a pending join link behind.
  // Preserve the verified identity boundary across reloads, while distinguishing a
  // deliberate sign-out from an unexpected session revocation in the UI.
  try { window.localStorage.setItem(signedOutAccountKey, "1"); }
  catch { /* localStorage may be unavailable; the backend already ended the session */ }
}
