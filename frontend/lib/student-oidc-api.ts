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

/** A short classroom code is never an authentication credential: OIDC is mandatory. */
export async function joinStudentClassroom(code: string): Promise<ClaimedEnrollment> {
  const normalized = code.trim().toUpperCase();
  if (!/^[A-Z0-9_-]{8,40}$/.test(normalized)) {
    throw new Error("Informe o código da turma (8 a 40 caracteres).");
  }
  const response = await apiFetch("/api/student-account/classrooms/join", {
    method: "POST",
    body: JSON.stringify({ code: normalized }),
  });
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      throw new Error("Faça login novamente com uma conta verificada.");
    }
    if (response.status === 429) {
      throw new Error("Muitas tentativas. Aguarde antes de tentar novamente.");
    }
    throw new Error(
      "Não foi possível ingressar. Confirme o código e a liberação da turma. Se já possui cadastro, peça ao professor para conferir seu e-mail.",
    );
  }
  return (await response.json()) as ClaimedEnrollment;
}

export async function logoutStudentOidc(): Promise<void> {
  const response = await apiFetch("/api/student-account/logout", { method: "POST" });
  if (!response.ok) throw new Error("Não foi possível sair desta conta.");
  // Preserve the verified identity boundary across reloads, while distinguishing a
  // deliberate sign-out from an unexpected session revocation in the UI.
  try { window.localStorage.setItem(signedOutAccountKey, "1"); }
  catch { /* localStorage may be unavailable; the backend already ended the session */ }
}
