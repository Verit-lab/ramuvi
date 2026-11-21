"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { fetchStudentCapabilities } from "@/lib/student-capabilities-api";
import { LibraryAccessError, fetchMyLibrary, formatLabel, removeMyLibraryItem, type LibraryItemSummary } from "@/lib/student-library-api";
import styles from "@/components/study/study.module.css";

const dateFormat = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" });

type State =
  | { kind: "loading" }
  | { kind: "off" }
  | { kind: "signed-out" }
  | { kind: "error"; message: string }
  | { kind: "ready"; items: LibraryItemSummary[] };

/** Minha Biblioteca (ADR-0076 D8): the account's collection, with or without a classroom. */
export default function MyLibraryPage() {
  const [state, setState] = useState<State>({ kind: "loading" });
  const [removing, setRemoving] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const items = await fetchMyLibrary();
        if (active) setState({ kind: "ready", items });
      } catch (error) {
        if (!active) return;
        if (error instanceof LibraryAccessError && error.kind === "ACCOUNT_REQUIRED") {
          setState({ kind: "signed-out" });
        } else if (error instanceof LibraryAccessError && error.kind === "UNAVAILABLE") {
          const capabilities = await fetchStudentCapabilities();
          if (active) setState(capabilities.personalLibrary ? { kind: "error", message: error.message } : { kind: "off" });
        } else {
          setState({ kind: "error", message: error instanceof Error ? error.message : "Não foi possível abrir sua Biblioteca." });
        }
      }
    })();
    return () => { active = false; };
  }, [attempt]);

  async function remove(item: LibraryItemSummary) {
    if (!window.confirm(`Remover “${item.title}” da sua Biblioteca? O material do professor não é apagado.`)) return;
    setRemoving(item.id);
    try {
      await removeMyLibraryItem(item.id);
      setState((current) => current.kind === "ready" ? { kind: "ready", items: current.items.filter((value) => value.id !== item.id) } : current);
      setNotice(`“${item.title}” foi removido da sua Biblioteca.`);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "Não foi possível remover.");
    } finally {
      setRemoving(null);
    }
  }

  return <>
    <p className={styles.eyebrow}>Minha Biblioteca</p>
    <h1 className={styles.title}>Materiais que você guardou.</h1>
    <p className={styles.lead}>Materiais salvos por links de professores ficam na sua conta, em qualquer aparelho, mesmo depois que a aula acaba.</p>
    {notice && <p role="status" className={styles.liveLoadStatus}>{notice}</p>}
    {state.kind === "loading" && <div className={styles.empty} role="status">Carregando sua Biblioteca…</div>}
    {state.kind === "off" && <div className={styles.empty}><strong>Minha Biblioteca ainda não está disponível.</strong><p>Ela será liberada quando a escola ativar esta função.</p></div>}
    {state.kind === "signed-out" && <div className={styles.empty}>
      <strong>Entre com sua conta de estudante.</strong>
      <p>A Biblioteca pertence à sua conta; sem entrar, não há como mostrar seus materiais.</p>
      <Link className={styles.button} href="/learn?account=1">Entrar</Link>
    </div>}
    {state.kind === "error" && <div className={styles.error} role="alert">
      <strong>Não foi possível abrir sua Biblioteca.</strong><p>{state.message}</p>
      <button type="button" className={styles.button} onClick={() => { setState({ kind: "loading" }); setAttempt((value) => value + 1); }}>Tentar novamente</button>
    </div>}
    {state.kind === "ready" && state.items.length === 0 && <div className={styles.empty} data-testid="my-library-empty">
      <strong>Sua Biblioteca está vazia.</strong>
      <p>Quando um professor compartilhar um material com a opção de salvar, abra o link e toque em “Salvar na Minha Biblioteca”.</p>
    </div>}
    {state.kind === "ready" && state.items.length > 0 && <section className={styles.section} aria-label="Materiais salvos">
      <ul className={styles.memberLiveList} data-testid="my-library-list">
        {state.items.map((item) => <li key={item.id} className={styles.card}>
          <div>
            <h3><Link href={`/learn/library/${item.id}`}>{item.title}</Link></h3>
            <p>{formatLabel(item.format)} · versão {item.revision} · salvo em {dateFormat.format(new Date(item.savedAt))}</p>
          </div>
          <button type="button" className={styles.errorSecondary} disabled={removing === item.id} onClick={() => void remove(item)}
            aria-label={`Remover ${item.title} da Minha Biblioteca`}>
            {removing === item.id ? "Removendo…" : "Remover"}
          </button>
        </li>)}
      </ul>
    </section>}
  </>;
}
