"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import LibraryMaterialView from "@/components/library/LibraryMaterialView";
import {
  LibraryAccessError,
  captureShareTokenFromLocation,
  clearPendingShareToken,
  previewSharedMaterial,
  saveResultMessage,
  saveSharedMaterial,
  type SaveResult,
  type SharedMaterial,
} from "@/lib/student-library-api";
import styles from "@/components/study/study.module.css";

type State =
  | { kind: "loading" }
  | { kind: "missing" }
  | { kind: "error"; error: LibraryAccessError }
  | { kind: "ready"; material: SharedMaterial };

/**
 * Public preview of a Library share (ADR-0076 D7). The token is read from #acesso=, removed from
 * the address bar before any request and sent only in POST bodies. VIEW links open without an
 * account and never record progress; SAVE links require a verified student account.
 */
export default function SharedMaterialPage() {
  const [token, setToken] = useState<string | null>(null);
  const [state, setState] = useState<State>({ kind: "loading" });
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState<SaveResult | null>(null);
  const [saveError, setSaveError] = useState("");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const captured = captureShareTokenFromLocation();
    let active = true;
    void (async () => {
      if (!captured) { if (active) setState({ kind: "missing" }); return; }
      setToken(captured);
      try {
        const material = await previewSharedMaterial(captured);
        if (active) setState({ kind: "ready", material });
      } catch (error) {
        if (!active) return;
        const typed = error instanceof LibraryAccessError ? error : new LibraryAccessError("Não foi possível abrir o material.", "NETWORK", 0);
        if (typed.kind === "UNAVAILABLE" || typed.kind === "INVALID") clearPendingShareToken();
        setState({ kind: "error", error: typed });
      }
    })();
    return () => { active = false; };
  }, [attempt]);

  async function save(replaceVersion: boolean) {
    if (!token) return;
    setSaving(true);
    setSaveError("");
    try {
      const result = await saveSharedMaterial(token, replaceVersion);
      setSaved(result);
      if (!result.newerVersionAvailable) clearPendingShareToken();
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : "Não foi possível salvar.");
    } finally {
      setSaving(false);
    }
  }

  if (state.kind === "loading") return <main className={styles.main} role="status">Abrindo material…</main>;
  if (state.kind === "missing") {
    return <main className={styles.main}><div className={styles.empty} role="alert">
      <strong>Link incompleto.</strong>
      <p>Abra novamente o link ou QR recebido. O código de acesso fica depois do símbolo #.</p>
    </div></main>;
  }
  if (state.kind === "error") {
    const { error } = state;
    return <main className={styles.main}><div className={styles.error} role="alert" data-testid="shared-material-error" data-kind={error.kind}>
      <strong>{error.kind === "ACCOUNT_REQUIRED" ? "Entre para abrir este material." : "Material indisponível."}</strong>
      <p>{error.message}</p>
      {error.kind === "ACCOUNT_REQUIRED" && <Link className={styles.button} href="/learn?account=1">Entrar com conta de estudante</Link>}
      {(error.kind === "NETWORK" || error.kind === "RATE_LIMITED") && <button type="button" className={styles.button} onClick={() => { setState({ kind: "loading" }); setAttempt((value) => value + 1); }}>Tentar novamente</button>}
    </div></main>;
  }

  const { material } = state;
  return <main className={styles.main}>
    <div className={styles.readerView}>
      <LibraryMaterialView material={material} />
      <section className={styles.readerFooter} aria-label="Ações do material">
        {material.canSave ? <div>
          {saved ? <p role="status" data-testid="shared-material-saved">{saveResultMessage(saved)} <Link href="/learn/library">Abrir Minha Biblioteca</Link></p>
            : material.alreadySaved && !material.savedVersionDiffers ? <p role="status">Este material já está na sua Biblioteca. <Link href="/learn/library">Abrir Minha Biblioteca</Link></p>
              : <p>{material.savedVersionDiffers ? "Você já salvou outra versão deste material." : "Guarde este material na sua conta para ler em qualquer aparelho."}</p>}
          {saveError && <p role="alert">{saveError}</p>}
        </div> : <p>Somente visualização. Este link não salva o material nem registra progresso.</p>}
        {material.canSave && !saved && (!material.alreadySaved || material.savedVersionDiffers) && <button type="button" className={styles.readerPrimary} disabled={saving}
          onClick={() => void save(material.savedVersionDiffers)} data-testid="shared-material-save">
          {saving ? "Salvando…" : material.savedVersionDiffers ? "Substituir pela versão deste link" : "Salvar na Minha Biblioteca"}
        </button>}
        {saved?.newerVersionAvailable && <button type="button" className={styles.readerPrimary} disabled={saving} onClick={() => void save(true)}>Substituir pela versão deste link</button>}
      </section>
    </div>
  </main>;
}
