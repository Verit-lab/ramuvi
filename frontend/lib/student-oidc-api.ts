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

export async function logoutStudentOidc(): Promise<void> {
  const response = await apiFetch("/api/student-account/logout", { method: "POST" });
  if (!response.ok) throw new Error("Não foi possível sair desta conta.");
}
