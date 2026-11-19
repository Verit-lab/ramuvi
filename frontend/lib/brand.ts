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
  tagline: "Prepare a aula, conduza ao vivo e acompanhe cada aluno.",
  description:
    "A Ramuvi reúne jornadas de aprendizagem, atividades e aulas ao vivo em uma plataforma que conecta professores e alunos.",
  /**
   * Company responsible for the platform. Owner decision (2026-11-19): the
   * only accepted spelling is "Verit" (short form) and "Verit Soluções
   * Digitais LTDA" (legal documents). The CNPJ is NOT published until it is
   * confirmed against the official registry (see LEGAL_STATUS.pending).
   */
  publisher: "Verit",
  company: "Verit Soluções Digitais LTDA",
} as const;

/**
 * Publication gate of the legal documents. While `approved` is false the
 * pages render the pending items, stay out of search indexes and never claim
 * an effective date. Flip only with legal sign-off and the confirmed data.
 */
export const LEGAL_STATUS = {
  approved: false,
  /** Date of the current text revision (not an effective date). */
  revisedOn: "19 de novembro de 2026",
  pending: [
    "CNPJ e endereço da Verit Soluções Digitais LTDA, conferidos no cadastro oficial da Receita Federal.",
    "Papel de cada parte (controladora ou operadora) em cada contrato com instituição de ensino.",
    "Canal oficial de atendimento e de privacidade, e o encarregado pelo tratamento de dados.",
    "Prazos de retenção por categoria de dado, inclusive cópias de segurança e registros técnicos.",
    "Lista de fornecedores de infraestrutura e eventuais transferências internacionais.",
    "Revisão e aprovação jurídica do texto final.",
  ],
} as const;

export const LEGAL_ROUTES = {
  terms: "/termos",
  privacy: "/privacidade",
} as const;

/** Canonical entry points. Sub-routes keep their existing domain paths. */
export const ENTRY_ROUTES = {
  landing: "/",
  learn: "/learn",
  teach: "/teach",
  liveJoin: "/join",
} as const;
