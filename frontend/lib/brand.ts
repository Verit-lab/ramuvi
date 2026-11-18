/**
 * Public identity of the platform (v0.12 rebranding). One platform, two
 * environments: Learn for students and Teach for teachers. Use these values
 * for visible copy, metadata and accessible names instead of literals so a
 * future copy change stays in one place.
 *
 * Persisted browser keys (localStorage/sessionStorage/Cache Storage) still use
 * the historical `arena-dev` prefix on purpose: renaming them would silently
 * drop remembered device access, theme and study preferences. See
 * docs/v0.12.1/RAMUVI_BRAND_AND_ROUTES.md.
 */
export const BRAND = {
  name: "Ramuvi",
  learn: "Ramuvi Learn",
  teach: "Ramuvi Teach",
  tagline: "Ensinar e aprender, em uma nova experiência.",
  description:
    "A Ramuvi reúne jornadas de aprendizagem, atividades interativas e experiências ao vivo em uma plataforma que conecta professores e alunos.",
} as const;

/** Canonical entry points. Sub-routes keep their existing domain paths. */
export const ENTRY_ROUTES = {
  landing: "/",
  learn: "/learn",
  teach: "/teach",
  liveJoin: "/join",
} as const;
