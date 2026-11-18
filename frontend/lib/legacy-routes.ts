/**
 * v0.12: "/" became the public landing page and the teacher area moved to
 * "/teach". Before that, the whole teacher area lived at "/" and kept its open
 * screen in the query string (lib/teacher-location.ts), and the backend OAuth
 * integration callbacks returned to "/?<provider>=connected". Those URLs are
 * bookmarked, shared and installed, so a request to "/" carrying any of these
 * parameters is sent to the same query on "/teach".
 *
 * Only an allow-list of known parameters triggers the redirect, the
 * destination is always the fixed same-origin path "/teach" and the original
 * query is copied verbatim: no parameter can choose the redirect target.
 */
export const LEGACY_TEACHER_QUERY_PARAMS = [
  // lib/teacher-location.ts (screen, classroom, tab, journey creation)
  "tela",
  "turma",
  "aba",
  "jornada",
  // "Editar jornada" from the teacher preview of a Journey
  "journey",
  // Delegated OAuth integrations returning to the teacher area
  "google",
  "microsoft",
  "notion",
] as const;

export const TEACH_PATH = "/teach";

/**
 * Returns the "/teach" URL (path + query) a legacy root request must be sent
 * to, or null when the request is a plain visit to the landing page.
 */
export function legacyTeacherRedirect(pathname: string, search: string): string | null {
  if (pathname !== "/") return null;
  const params = new URLSearchParams(search);
  const isLegacyTeacherLink = LEGACY_TEACHER_QUERY_PARAMS.some((name) => params.has(name));
  if (!isLegacyTeacherLink) return null;
  const query = params.toString();
  return `${TEACH_PATH}${query ? `?${query}` : ""}`;
}
