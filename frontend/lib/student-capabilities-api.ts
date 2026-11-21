import { apiFetch } from "./auth-api";

export type StudentCapabilities = { liveMemberJoin: boolean; personalLibrary: boolean };

export const STUDENT_CAPABILITIES_OFF: StudentCapabilities = { liveMemberJoin: false, personalLibrary: false };

/**
 * v0.12.2 student switches. 401/403/404 (no OIDC session, OIDC off, older backend) and network
 * errors all mean OFF: the UI never announces a feature the backend does not expose.
 */
export async function fetchStudentCapabilities(): Promise<StudentCapabilities> {
  try {
    const response = await apiFetch("/api/student-account/capabilities");
    if (!response.ok) return STUDENT_CAPABILITIES_OFF;
    const body = (await response.json()) as Partial<StudentCapabilities>;
    return { liveMemberJoin: body.liveMemberJoin === true, personalLibrary: body.personalLibrary === true };
  } catch {
    return STUDENT_CAPABILITIES_OFF;
  }
}
