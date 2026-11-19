import type { Metadata } from "next";
import Link from "next/link";
import RamuviMark from "@/components/brand/RamuviMark";
import LegalLinks from "@/components/legal/LegalLinks";
import { BRAND, ENTRY_ROUTES } from "@/lib/brand";
import styles from "./landing.module.css";

const PAGE_TITLE = `${BRAND.name} · Prepare, conduza e acompanhe suas aulas`;

export const metadata: Metadata = {
  title: { absolute: PAGE_TITLE },
  description: BRAND.description,
  openGraph: {
    type: "website",
    locale: "pt_BR",
    siteName: BRAND.name,
    title: PAGE_TITLE,
    description: BRAND.description,
    // No og:image: absolute image URLs need a build-time public origin
    // (metadataBase), which this deployment does not configure yet.
  },
  twitter: {
    card: "summary",
    title: BRAND.name,
    description: BRAND.description,
  },
};

/*
 * Copy rule: every sentence below describes something this release does.
 * No metrics, testimonials, logos or roadmap items (offline study is still a
 * draft behind a flag and is deliberately not mentioned as available).
 */

type Phase = { id: string; name: string; teach: string; learn: string };

/** The real sequence of a class in the product, from both seats. */
const PHASES: Phase[] = [
  {
    id: "preparar",
    name: "Preparar",
    teach: "Monte a jornada em capítulos, reaproveite materiais da biblioteca e crie atividades com questões e entregas.",
    learn: "Recebe o convite da turma e encontra os conteúdos liberados, na ordem em que fazem sentido.",
  },
  {
    id: "conduzir",
    name: "Conduzir",
    teach: "Abra a aula ao vivo no projetor e use quiz, votação, nuvem de palavras, buzzer, sorteio e cronômetro.",
    learn: "Entra pelo código ou QR da aula, responde pelo celular e acompanha a dinâmica junto com a turma.",
  },
  {
    id: "acompanhar",
    name: "Acompanhar",
    teach: "Corrija entregas com critérios, devolva comentários e consulte o histórico e o ranking de cada aula.",
    learn: "Vê o próprio progresso, retoma de onde parou e recebe a devolutiva do professor.",
  },
];

const TEACH_POINTS = [
  "Turmas com alunos e convites individuais",
  "Jornadas com liberação gradual de capítulos",
  "Atividades com questões, entregas, critérios e devolutiva",
  "Biblioteca de materiais reaproveitáveis",
  "Aulas ao vivo com tela de projeção",
  "Histórico das aulas e ranking da turma",
];

const LEARN_POINTS = [
  "Jornadas organizadas em capítulos e conteúdos",
  "Progresso salvo para continuar de onde parou",
  "Atividades e devolutivas do professor",
  "Conquistas que reconhecem a participação",
  "Ajuste do tamanho do texto e redução de animações",
  "Entrada nas aulas ao vivo da turma",
];

/** Product decisions a visitor can verify in use; not marketing claims. */
const COMMITMENTS = [
  {
    title: "Pontuação não é nota",
    text: "XP, ranking e conquistas reconhecem participação. A avaliação continua sendo do professor, com critérios e devolutiva.",
  },
  {
    title: "Cada aluno, um convite",
    text: "O vínculo com a turma usa convite individual. O código da aula ao vivo vale só para aquela aula.",
  },
  {
    title: "No ritmo da turma",
    text: "O professor decide quando cada capítulo é liberado. O aluno vê o que já está disponível, sem antecipar o restante.",
  },
  {
    title: "Leitura confortável",
    text: "Navegação por teclado, temas claro e escuro, ajuste de texto e respeito à preferência por menos movimento.",
  },
];

const FAQ = [
  {
    question: "Preciso criar uma conta para participar de uma aula ao vivo?",
    answer: "Não. Basta o código ou o QR mostrado pelo professor. A conta é usada para acessar jornadas e o progresso nas turmas.",
  },
  {
    question: "Como o aluno entra na turma?",
    answer: "Quando a instituição habilita o acesso por conta, o aluno entra com uma conta verificada (por exemplo, Google) e confirma o convite individual enviado pelo professor.",
  },
  {
    question: "XP e ranking contam como nota?",
    answer: "Não. São formas de reconhecer participação. Notas e avaliações formais seguem as regras da instituição e são definidas pelo professor.",
  },
  {
    question: "Quem vê os dados dos alunos?",
    answer: "O professor das turmas às quais o aluno está vinculado. Na aula ao vivo, a tela de projeção pode mostrar à turma o nome de exibição e a pontuação; o professor decide se o ranking aparece e se fica anônimo. Os detalhes estão na Política de Privacidade.",
  },
  {
    question: "A Ramuvi funciona sem internet?",
    answer: "Ainda não. Hoje é preciso estar conectado para estudar e participar das aulas.",
  },
];

/**
 * Public landing page. Static (no session lookup, no client JavaScript of its
 * own): it presents the platform and links to the two environments, which do
 * their own authentication. The live-class code form is a plain GET to /join,
 * which already reads `?code=`. Legacy teacher links to "/" are redirected to
 * "/teach" by proxy.ts before this page renders.
 */
export default function LandingPage() {
  return (
    <div className={styles.page}>
      <a className={styles.skipLink} href="#conteudo">Pular para o conteúdo</a>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link href={ENTRY_ROUTES.landing} className={styles.logo} aria-label={`${BRAND.name}, página inicial`}>
            <RamuviMark size={30} />
            <span>{BRAND.name}</span>
          </Link>
          <nav className={styles.nav} aria-label="Navegação principal">
            <a href="#como-funciona" className={styles.navAnchor}>Como funciona</a>
            <a href="#perguntas" className={styles.navAnchor}>Perguntas</a>
            <Link href={ENTRY_ROUTES.learn}>Sou aluno</Link>
            <Link href={ENTRY_ROUTES.teach} className={styles.navCta}>Sou professor</Link>
          </nav>
        </div>
      </header>

      <main id="conteudo" tabIndex={-1} className={styles.main}>
        <section className={styles.hero} aria-labelledby="hero-title">
          <div className={styles.heroCopy}>
            <h1 id="hero-title" className={styles.heroTitle}>{BRAND.tagline}</h1>
            <p className={styles.heroText}>
              {BRAND.teach} organiza o trabalho do professor. {BRAND.learn} dá ao aluno um lugar
              para estudar e participar. As mesmas turmas e os mesmos conteúdos, nos dois lados da aula.
            </p>
            <div className={styles.heroActions}>
              <Link href={ENTRY_ROUTES.teach} className={styles.primary}>Sou professor</Link>
              <Link href={ENTRY_ROUTES.learn} className={styles.secondary}>Sou aluno</Link>
            </div>
          </div>

          <form className={styles.codeForm} action={ENTRY_ROUTES.liveJoin} method="get" aria-labelledby="code-title">
            <h2 id="code-title" className={styles.codeTitle}>Vai entrar em uma aula agora?</h2>
            <p id="code-help" className={styles.codeHelp}>Digite o código que o professor mostrou na tela.</p>
            <div className={styles.codeRow}>
              <label htmlFor="landing-code" className={styles.visuallyHidden}>Código da aula</label>
              <input
                id="landing-code"
                name="code"
                className={styles.codeInput}
                aria-describedby="code-help"
                autoComplete="off"
                autoCapitalize="characters"
                spellCheck={false}
                maxLength={8}
                placeholder="ABC123"
                required
              />
              <button type="submit" className={styles.codeSubmit}>Entrar na aula</button>
            </div>
          </form>
        </section>

        <section id="como-funciona" className={styles.flow} aria-labelledby="flow-title">
          <div className={styles.sectionHead}>
            <h2 id="flow-title" className={styles.sectionTitle}>Uma aula, dos dois lados</h2>
            <p className={styles.sectionLead}>
              O que o professor faz no {BRAND.teach} aparece para o aluno no {BRAND.learn}, em cada etapa.
            </p>
          </div>
          <ol className={styles.phases}>
            {PHASES.map((phase, index) => (
              <li key={phase.id} className={styles.phase} aria-labelledby={`phase-${phase.id}`}>
                <h3 id={`phase-${phase.id}`} className={styles.phaseName}>
                  <span className={styles.phaseIndex} aria-hidden="true">{index + 1}</span>
                  {phase.name}
                </h3>
                <dl className={styles.phaseRoles}>
                  <div className={styles.roleTeach}>
                    <dt>Professor</dt>
                    <dd>{phase.teach}</dd>
                  </div>
                  <div className={styles.roleLearn}>
                    <dt>Aluno</dt>
                    <dd>{phase.learn}</dd>
                  </div>
                </dl>
              </li>
            ))}
          </ol>
        </section>

        <section id="ambientes" className={styles.environments} aria-labelledby="ambientes-title">
          <h2 id="ambientes-title" className={styles.visuallyHidden}>Os dois ambientes da Ramuvi</h2>
          <article className={styles.envTeach} aria-labelledby="env-teach">
            <p className={styles.envRole}>Para professores</p>
            <h3 id="env-teach">{BRAND.teach}</h3>
            <p className={styles.envLead}>Prepare, conduza e acompanhe as turmas em um só lugar.</p>
            <ul>{TEACH_POINTS.map((point) => <li key={point}>{point}</li>)}</ul>
            <Link href={ENTRY_ROUTES.teach} className={styles.envLink}>Acessar Teach</Link>
          </article>
          <article className={styles.envLearn} aria-labelledby="env-learn">
            <p className={styles.envRole}>Para alunos</p>
            <h3 id="env-learn">{BRAND.learn}</h3>
            <p className={styles.envLead}>Estude no seu ritmo e participe das aulas da turma.</p>
            <ul>{LEARN_POINTS.map((point) => <li key={point}>{point}</li>)}</ul>
            <Link href={ENTRY_ROUTES.learn} className={styles.envLink}>Acessar Learn</Link>
          </article>
        </section>

        <section className={styles.commitments} aria-labelledby="commitments-title">
          <h2 id="commitments-title" className={styles.sectionTitle}>Decisões que você percebe no uso</h2>
          <dl className={styles.commitmentList}>
            {COMMITMENTS.map((item) => (
              <div key={item.title}>
                <dt>{item.title}</dt>
                <dd>{item.text}</dd>
              </div>
            ))}
          </dl>
        </section>

        <section id="perguntas" className={styles.faq} aria-labelledby="faq-title">
          <h2 id="faq-title" className={styles.sectionTitle}>Perguntas frequentes</h2>
          <div className={styles.faqList}>
            {FAQ.map((item) => (
              <details key={item.question} className={styles.faqItem}>
                <summary>{item.question}</summary>
                <p>{item.answer}</p>
              </details>
            ))}
          </div>
        </section>
      </main>

      <footer className={styles.footer}>
        <div className={styles.footerInner}>
          <div className={styles.footerBrand}>
            <RamuviMark size={22} />
            <p><strong>{BRAND.name}</strong>, uma plataforma da {BRAND.publisher}.</p>
          </div>
          <nav aria-label="Ambientes" className={styles.footerNav}>
            <Link href={ENTRY_ROUTES.teach}>{BRAND.teach}</Link>
            <Link href={ENTRY_ROUTES.learn}>{BRAND.learn}</Link>
            <Link href={ENTRY_ROUTES.liveJoin}>Entrar em uma aula</Link>
          </nav>
          <LegalLinks className={styles.footerLegal} />
        </div>
      </footer>
    </div>
  );
}
