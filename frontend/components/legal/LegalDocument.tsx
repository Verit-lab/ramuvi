import Link from "next/link";
import RamuviMark from "@/components/brand/RamuviMark";
import LegalLinks from "@/components/legal/LegalLinks";
import { BRAND, ENTRY_ROUTES, LEGAL_STATUS } from "@/lib/brand";
import styles from "./legal-document.module.css";

export type LegalSection = {
  id: string;
  title: string;
  paragraphs?: string[];
  items?: string[];
};

type Props = {
  title: string;
  intro: string;
  sections: LegalSection[];
};

/**
 * Shared layout of /termos and /privacidade. While LEGAL_STATUS.approved is
 * false it shows the specific pending items (never an effective date); the
 * pages also set robots noindex while pending.
 */
export default function LegalDocument({ title, intro, sections }: Props) {
  return (
    <div className={styles.page}>
      <a className={styles.skipLink} href="#conteudo">Pular para o conteúdo</a>
      <header className={styles.header}>
        <Link className={styles.brand} href={ENTRY_ROUTES.landing} aria-label={`${BRAND.name}, página inicial`}>
          <RamuviMark size={26} />
          <span>{BRAND.name}</span>
        </Link>
        <Link className={styles.back} href={ENTRY_ROUTES.landing}>Início</Link>
      </header>
      <main id="conteudo" tabIndex={-1} className={styles.main}>
        <h1>{title}</h1>
        <p className={styles.intro}>{intro}</p>
        <p className={styles.updated}>
          {LEGAL_STATUS.approved
            ? `Versão de ${LEGAL_STATUS.revisedOn}.`
            : `Texto revisado em ${LEGAL_STATUS.revisedOn}. Ainda sem data de vigência.`}
        </p>

        {!LEGAL_STATUS.approved && (
          <aside className={styles.draft} aria-labelledby="pendencias-title">
            <h2 id="pendencias-title">Minuta em revisão</h2>
            <p>
              Este texto descreve o funcionamento atual da {BRAND.name}, mas ainda não foi aprovado
              juridicamente e não substitui contrato com a instituição de ensino. Antes da
              publicação definitiva, faltam:
            </p>
            <ul>{LEGAL_STATUS.pending.map((item) => <li key={item}>{item}</li>)}</ul>
          </aside>
        )}

        <nav className={styles.toc} aria-label="Nesta página">
          <ol>
            {sections.map((section) => (
              <li key={section.id}><a href={`#${section.id}`}>{section.title}</a></li>
            ))}
          </ol>
        </nav>

        {sections.map((section) => (
          <section className={styles.section} key={section.id} id={section.id} aria-labelledby={`${section.id}-title`}>
            <h2 id={`${section.id}-title`}>{section.title}</h2>
            {section.paragraphs?.map((paragraph) => <p key={paragraph}>{paragraph}</p>)}
            {section.items && <ul>{section.items.map((item) => <li key={item}>{item}</li>)}</ul>}
          </section>
        ))}
      </main>
      <footer className={styles.footer}>
        <p>{BRAND.name} · {BRAND.company}</p>
        <LegalLinks className={styles.footerLegal} />
      </footer>
    </div>
  );
}
