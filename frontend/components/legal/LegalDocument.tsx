import Link from "next/link";
import styles from "./legal-document.module.css";

export type LegalSection = {
  title: string;
  paragraphs?: string[];
  items?: string[];
};

type Props = {
  title: string;
  intro: string;
  sections: LegalSection[];
};

export default function LegalDocument({ title, intro, sections }: Props) {
  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <Link className={styles.brand} href="/">ramuvi</Link>
        <nav className={styles.navigation} aria-label="Documentos e ambientes">
          <Link href="/learn">Learn</Link>
          <Link href="/teach">Teach</Link>
          <Link href="/termos">Termos de Uso</Link>
          <Link href="/privacidade">Privacidade</Link>
        </nav>
      </header>
      <main id="conteudo" className={styles.main}>
        <p className={styles.eyebrow}>Transparência e responsabilidade</p>
        <h1>{title}</h1>
        <p className={styles.intro}>{intro}</p>
        <p className={styles.draft} role="note">
          <strong>Minuta em revisão.</strong> A identificação do responsável legal, o canal de
          privacidade e os prazos de retenção precisam ser confirmados antes da
          aprovação definitiva destes documentos. Este texto não representa
          homologação jurídica nem aceite contratual.
        </p>
        <p className={styles.updated}>Minuta preparada em 18 de novembro de 2026.</p>
        {sections.map((section, index) => (
          <section className={styles.section} key={section.title} aria-labelledby={'section-' + index}>
            <h2 id={'section-' + index}>{section.title}</h2>
            {section.paragraphs?.map((paragraph) => <p key={paragraph}>{paragraph}</p>)}
            {section.items && <ul>{section.items.map((item) => <li key={item}>{item}</li>)}</ul>}
          </section>
        ))}
      </main>
      <footer className={styles.footer}>
        <span>Ramuvi · Ensino e aprendizagem</span>
        <nav aria-label="Navegação do rodapé">
          <Link href="/">Início</Link>
          <Link href="/termos">Termos de Uso</Link>
          <Link href="/privacidade">Política de Privacidade</Link>
        </nav>
      </footer>
    </div>
  );
}
