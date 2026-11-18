import type { Metadata } from "next";
import Link from "next/link";
import RamuviMark from "@/components/brand/RamuviMark";
import { BRAND, ENTRY_ROUTES } from "@/lib/brand";
import styles from "./landing.module.css";

export const metadata: Metadata = {
  title: { absolute: `${BRAND.name} · Ensinar e aprender, em uma nova experiência` },
  description: BRAND.description,
  openGraph: {
    type: "website",
    locale: "pt_BR",
    siteName: BRAND.name,
    title: `${BRAND.name} · Ensinar e aprender, em uma nova experiência`,
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

// Only capabilities that exist in this release. Do not list roadmap items here.
const LEARN_POINTS = [
  "Jornadas organizadas em capítulos e conteúdos",
  "Atividades e desafios da turma",
  "Progresso salvo para continuar de onde parou",
  "Conquistas pela participação",
  "Entrada nas aulas ao vivo da turma",
];

const TEACH_POINTS = [
  "Turmas, alunos e convites individuais",
  "Criação e liberação gradual de jornadas",
  "Atividades com questões, entregas e avaliação",
  "Condução de aulas ao vivo com projetor",
  "Ranking, histórico e acompanhamento da turma",
];

const FEATURES: { title: string; text: string; icon: FeatureIconName }[] = [
  { title: "Jornadas de aprendizagem", text: "Sequências de estudo com capítulos, materiais e liberação no ritmo da turma.", icon: "journey" },
  { title: "Atividades interativas", text: "Questões, entregas e avaliação supervisionada vinculadas a cada turma.", icon: "activity" },
  { title: "Experiências ao vivo", text: "Quiz, votação, nuvem de palavras, buzzer e sorteio, com entrada por código ou QR.", icon: "live" },
  { title: "Acompanhamento do progresso", text: "O aluno vê o próprio avanço; o professor acompanha a turma e o histórico das aulas.", icon: "progress" },
  { title: "Organização de conteúdos", text: "Biblioteca de materiais reaproveitáveis entre jornadas, atividades e aulas.", icon: "library" },
  { title: "Participação e conquistas", text: "Pontuação, ranking da turma e conquistas que reconhecem a participação.", icon: "achievement" },
];

type FeatureIconName = "journey" | "activity" | "live" | "progress" | "library" | "achievement";

function FeatureIcon({ name }: { name: FeatureIconName }) {
  const paths: Record<FeatureIconName, React.ReactNode> = {
    journey: <><circle cx="6" cy="18" r="2" /><circle cx="18" cy="6" r="2" /><path d="M8 18h5a3 3 0 0 0 0-6h-2a3 3 0 0 1 0-6h5" /></>,
    activity: <><rect x="4" y="3" width="16" height="18" rx="2" /><path d="m8 12 2.5 2.5L16 9" /></>,
    live: <><circle cx="12" cy="12" r="2" /><path d="M7.8 7.8a6 6 0 0 0 0 8.4M16.2 7.8a6 6 0 0 1 0 8.4M4.9 4.9a10 10 0 0 0 0 14.2M19.1 4.9a10 10 0 0 1 0 14.2" /></>,
    progress: <><path d="M4 20V10M10 20V4M16 20v-7M22 20H2" /></>,
    library: <><path d="M4 19V5a2 2 0 0 1 2-2h12v16H6a2 2 0 0 0-2 2Zm0 0a2 2 0 0 0 2 2h12" /><path d="M9 7h5" /></>,
    achievement: <><circle cx="12" cy="9" r="6" /><path d="m8.5 14-1.5 7 5-3 5 3-1.5-7" /></>,
  };
  return (
    <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
      {paths[name]}
    </svg>
  );
}

/**
 * Public landing page. Static (no session lookup, no client JavaScript of its
 * own): it presents the platform and links to the two environments, which do
 * their own authentication. Legacy teacher links to "/" are redirected to
 * "/teach" by proxy.ts before this page renders.
 */
export default function LandingPage() {
  return (
    <div className={styles.page}>
      <a className={styles.skipLink} href="#conteudo">Pular para o conteúdo</a>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link href={ENTRY_ROUTES.landing} className={styles.logo} aria-label={`${BRAND.name}, página inicial`}>
            <RamuviMark size={32} />
            <span>{BRAND.name}</span>
          </Link>
          <nav className={styles.nav} aria-label="Navegação principal">
            <a href="#ambientes">Conheça a plataforma</a>
            <Link href={ENTRY_ROUTES.learn}>Sou aluno</Link>
            <Link href={ENTRY_ROUTES.teach} className={styles.navCta}>Sou professor</Link>
          </nav>
        </div>
      </header>

      <main id="conteudo" tabIndex={-1} className={styles.main}>
        <section className={styles.hero} aria-labelledby="hero-title">
          <p className={styles.eyebrow}>Plataforma educacional</p>
          <h1 id="hero-title" className={styles.heroTitle}>{BRAND.tagline}</h1>
          <p className={styles.heroText}>{BRAND.description}</p>
          <div className={styles.heroActions}>
            <Link href={ENTRY_ROUTES.learn} className={styles.primary}>Sou aluno</Link>
            <Link href={ENTRY_ROUTES.teach} className={styles.secondary}>Sou professor</Link>
          </div>
          <p className={styles.heroNote}>
            Vai participar de uma aula agora? <Link href={ENTRY_ROUTES.liveJoin}>Entre com o código da aula</Link>.
          </p>
        </section>

        <section id="ambientes" className={styles.section} aria-labelledby="ambientes-title">
          <h2 id="ambientes-title" className={styles.sectionTitle}>Dois ambientes, uma plataforma</h2>
          <p className={styles.sectionLead}>As mesmas turmas, os mesmos conteúdos e a mesma aula, com a experiência adequada a cada papel.</p>
          <div className={styles.environments}>
            <article className={styles.environment} aria-labelledby="env-learn">
              <p className={styles.envKicker}>Ambiente do estudante</p>
              <h3 id="env-learn">{BRAND.learn}</h3>
              <ul>{LEARN_POINTS.map((point) => <li key={point}>{point}</li>)}</ul>
              <Link href={ENTRY_ROUTES.learn} className={styles.primary}>Acessar Learn</Link>
            </article>
            <article className={styles.environment} aria-labelledby="env-teach">
              <p className={styles.envKicker}>Ambiente do professor</p>
              <h3 id="env-teach">{BRAND.teach}</h3>
              <ul>{TEACH_POINTS.map((point) => <li key={point}>{point}</li>)}</ul>
              <Link href={ENTRY_ROUTES.teach} className={styles.primary}>Acessar Teach</Link>
            </article>
          </div>
        </section>

        <section className={styles.section} aria-labelledby="recursos-title">
          <h2 id="recursos-title" className={styles.sectionTitle}>O que você encontra na Ramuvi</h2>
          <ul className={styles.features}>
            {FEATURES.map((feature) => (
              <li key={feature.title} className={styles.feature}>
                <span className={styles.featureIcon}><FeatureIcon name={feature.icon} /></span>
                <h3>{feature.title}</h3>
                <p>{feature.text}</p>
              </li>
            ))}
          </ul>
        </section>
      </main>

      <footer className={styles.footer}>
        <div className={styles.footerInner}>
          <div className={styles.footerBrand}>
            <RamuviMark size={24} />
            <span><strong>{BRAND.name}</strong> · plataforma de ensino e aprendizagem</span>
          </div>
          <nav aria-label="Ambientes" className={styles.footerNav}>
            <Link href={ENTRY_ROUTES.learn}>{BRAND.learn}</Link>
            <Link href={ENTRY_ROUTES.teach}>{BRAND.teach}</Link>
            <Link href={ENTRY_ROUTES.liveJoin}>Entrar em uma aula</Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
