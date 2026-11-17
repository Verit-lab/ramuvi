import { apiFetch } from "./auth-api";

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
  if (response.status === 401 || response.status === 404 || response.status === 403) return null;
  if (!response.ok) throw new Error("Não foi possível consultar sua sessão.");
  return (await response.json()) as StudentOidcSession;
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
      : response.status === 403
        ? "Sua sessão expirou ou não possui permissão. Entre novamente."
        : "Convite inválido, expirado ou associado a outra conta. Solicite um novo ao professor.");
  }
  return (await response.json()) as ClaimedEnrollment;
}

export async function logoutStudentOidc(): Promise<void> {
  const response = await apiFetch("/api/student-account/logout", { method: "POST" });
  if (!response.ok) throw new Error("Não foi possível sair desta conta.");
}
