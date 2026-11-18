import type { Metadata } from "next";
import ArenaApp from "@/components/ArenaApp";
import { BRAND } from "@/lib/brand";

export const metadata: Metadata = {
  title: { absolute: BRAND.teach },
  description: "Ambiente do professor: turmas, jornadas, atividades, avaliações e aulas ao vivo.",
  // The teacher area is private: authentication is enforced by the API
  // (ROLE_TEACHER); search engines should not index the entry either.
  robots: { index: false, follow: false },
};

/**
 * Ramuvi Teach: the teacher area. Same single-page app that used to be served
 * at "/"; ArenaApp validates the teacher session with the backend and shows
 * the login when there is none. The screen lives in the query string
 * (lib/teacher-location.ts), relative to this path.
 */
export default function TeachPage() {
  return <ArenaApp />;
}
