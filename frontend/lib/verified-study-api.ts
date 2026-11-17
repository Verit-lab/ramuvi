import { apiFetch } from "./auth-api";
import { fetchStudentOidcSession } from "./student-oidc-api";
import type {
  JourneyStudySummary,
  JourneyStudyView,
  JourneyContentDetailView,
} from "./study-api";

/**
 * Account access is never authorized by a local device claim. The server
 * resolves the OIDC issuer/subject and checks Student + active Enrollment for
 * every request. Tokens are not persisted in browser storage.
 */
const base = "/api/student-account/study/journeys";

export type VerifiedStudyMode = "ACCOUNT" | "DEVICE";
export type VerifiedStudyAccess = {
  mode: VerifiedStudyMode;
  journeys?: JourneyStudySummary[];
};

async function verifiedRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init);
  if (!response.ok) {
    const detail = response.status === 401
      ? "Sua sessão expirou. Entre novamente na conta."
      : response.status === 403
        ? "Esta conta não está autorizada para o conteúdo."
        : response.status === 404
          ? "Jornada não encontrada ou não autorizada para esta matrícula."
          : `Falha ao carregar Jornada (${response.status}).`;
    throw new Error(detail);
  }
  return (await response.json()) as T;
}

export async function verifiedStudyAccess(): Promise<VerifiedStudyAccess> {
  const session = await fetchStudentOidcSession();
  if (!session) return { mode: "DEVICE" };
  const response = await apiFetch(base);
  if (response.status === 404) {
    // The I3 feature flag is off: preserve the legacy classroom/code workflow.
    return { mode: "DEVICE" };
  }
  if (!response.ok) {
    throw new Error(response.status === 401 || response.status === 403
      ? "Sua conta não pode acessar Jornadas. Entre novamente."
      : `Falha ao consultar suas Jornadas (${response.status}).`);
  }
  return { mode: "ACCOUNT", journeys: (await response.json()) as JourneyStudySummary[] };
}

export function fetchVerifiedJourney(journeyId: string) {
  return verifiedRequest<JourneyStudyView>(`${base}/${encodeURIComponent(journeyId)}`);
}

export function fetchVerifiedContent(journeyId: string, contentId: string) {
  return verifiedRequest<JourneyContentDetailView>(
    `${base}/${encodeURIComponent(journeyId)}/contents/${encodeURIComponent(contentId)}`,
  );
}

export function startVerifiedContent(journeyId: string, contentId: string) {
  return verifiedRequest<JourneyStudyView>(
    `${base}/${encodeURIComponent(journeyId)}/contents/${encodeURIComponent(contentId)}/start`,
    { method: "POST", body: "{}" },
  );
}

export function completeVerifiedContent(journeyId: string, contentId: string) {
  return verifiedRequest<JourneyStudyView>(
    `${base}/${encodeURIComponent(journeyId)}/contents/${encodeURIComponent(contentId)}/complete`,
    { method: "POST", body: "{}" },
  );
}
