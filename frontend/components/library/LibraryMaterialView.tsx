"use client";

import { SafeMarkdown } from "@/components/SafeMarkdown";
import { blockOutsideEditable } from "@/lib/copy-protection";
import { formatLabel, type LibraryFormat } from "@/lib/student-library-api";
import styles from "@/components/study/study.module.css";

type Material = {
  title: string;
  summary: string | null;
  format: LibraryFormat;
  body: string | null;
  sourceUrl: string | null;
  sourceLabel: string | null;
  revision: number;
  copyProtected: boolean;
  externalReference: boolean;
};

function safeExternal(href: string | null): string | null {
  if (!href) return null;
  try {
    const url = new URL(href);
    return url.protocol === "https:" || url.protocol === "http:" ? url.toString() : null;
  } catch {
    return null;
  }
}

/**
 * Renders a delivered snapshot (ADR-0076 D9). Markdown goes through SafeMarkdown (raw HTML,
 * images and unsafe links never become DOM); plain text stays text; LINK is a reference whose
 * destination may change. Copy restriction is a UI deterrent, not DRM.
 */
export default function LibraryMaterialView({ material }: { material: Material }) {
  const external = safeExternal(material.sourceUrl);
  const protection = material.copyProtected ? {
    onCopy: blockOutsideEditable,
    onCut: blockOutsideEditable,
    onContextMenu: blockOutsideEditable,
    onDragStart: blockOutsideEditable,
  } : {};
  return <article className={`${styles.readerArticle} ${material.copyProtected ? styles.readerProtected : ""}`}
    data-copy-protected={material.copyProtected ? "true" : undefined} data-testid="library-material" {...protection}>
    <header className={styles.readerHeading}>
      <p className={styles.eyebrow}>{formatLabel(material.format)} · versão {material.revision}</p>
      <h1>{material.title}</h1>
      {material.summary && <p>{material.summary}</p>}
    </header>
    {material.copyProtected && <p className={styles.readerProtectedNote}>A cópia deste material foi desativada por quem o compartilhou.</p>}
    {material.body && material.format === "MARKDOWN" && <div className={styles.readerMarkdown}><SafeMarkdown>{material.body}</SafeMarkdown></div>}
    {material.body && material.format !== "MARKDOWN" && <div className={styles.readerPlainText} style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{material.body}</div>}
    {external && <div className={styles.readerSource}>
      <div><span>Referência externa</span><strong>{material.sourceLabel || new URL(external).hostname}</strong>
        <small>O conteúdo deste endereço pode mudar ou sair do ar; a Ramuvi guarda só a referência.</small></div>
      <a href={external} target="_blank" rel="noopener noreferrer">Abrir link</a>
    </div>}
  </article>;
}
