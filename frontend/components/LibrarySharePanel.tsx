"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { DialogCloseButton } from "@/components/ui";
import { useModalKeyboard } from "@/lib/use-modal-keyboard";
import {
  SHARE_VALIDITY_DAYS,
  createLibraryShare,
  fetchLibraryShareQr,
  listLibraryShares,
  permissionLabel,
  revokeLibraryShare,
  shareUrl,
  statusLabel,
  type IssuedShare,
  type SharePermission,
  type ShareSummary,
} from "@/lib/library-share-api";
import libraryStyles from "./LibraryWorkspace.module.css";
import styles from "./LibrarySharePanel.module.css";

const dateFormat = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" });

/**
 * v0.12.2 I3a: "Compartilhar" is distinct from "Adicionar a uma Jornada" (formal progress in a
 * classroom) and from presenting in the Arena (session-bound). The link needs no classroom,
 * shows its secret once and the list only shows state and aggregate counters.
 */
export default function LibrarySharePanel({
  resourceId,
  resourceTitle,
  onClose,
  notify,
}: {
  resourceId: string;
  resourceTitle: string;
  onClose: () => void;
  notify: (message: string, tone?: "info" | "error") => void;
}) {
  const [shares, setShares] = useState<ShareSummary[]>([]);
  const [loadError, setLoadError] = useState("");
  const [permission, setPermission] = useState<SharePermission>("VIEW");
  const [validDays, setValidDays] = useState<number>(14);
  const [label, setLabel] = useState("");
  const [busy, setBusy] = useState(false);
  const [issued, setIssued] = useState<IssuedShare | null>(null);
  const [qrUrl, setQrUrl] = useState<string | null>(null);
  const dialogRef = useRef<HTMLElement | null>(null);
  useModalKeyboard(true, onClose, dialogRef);

  const reload = useCallback(async () => {
    try {
      setShares(await listLibraryShares(resourceId));
      setLoadError("");
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Não foi possível carregar os links.");
    }
  }, [resourceId]);

  useEffect(() => {
    let active = true;
    void listLibraryShares(resourceId)
      .then((loaded) => { if (active) setShares(loaded); })
      .catch((error) => { if (active) setLoadError(error instanceof Error ? error.message : "Não foi possível carregar os links."); });
    return () => { active = false; };
  }, [resourceId]);

  useEffect(() => () => { if (qrUrl) URL.revokeObjectURL(qrUrl); }, [qrUrl]);

  async function create() {
    setBusy(true);
    try {
      const created = await createLibraryShare(resourceId, { permission, validDays, label });
      setIssued(created);
      setQrUrl(null);
      try {
        const blob = await fetchLibraryShareQr(window.location.origin, created.token);
        setQrUrl(URL.createObjectURL(blob));
      } catch {
        // The link itself stays usable; QR is a convenience.
      }
      await reload();
    } catch (error) {
      notify(error instanceof Error ? error.message : "Não foi possível criar o link.", "error");
    } finally {
      setBusy(false);
    }
  }

  async function copy() {
    if (!issued) return;
    try {
      await navigator.clipboard.writeText(shareUrl(window.location.origin, issued.path));
      notify("Link copiado.");
    } catch {
      notify("Não foi possível copiar automaticamente. Selecione o link e copie.", "error");
    }
  }

  async function revoke(share: ShareSummary) {
    if (!window.confirm("Revogar este link? Novas aberturas e novos salvamentos param; itens já salvos pelos alunos continuam com eles.")) return;
    setBusy(true);
    try {
      await revokeLibraryShare(share.id);
      notify("Link revogado.");
      await reload();
    } catch (error) {
      notify(error instanceof Error ? error.message : "Não foi possível revogar.", "error");
    } finally {
      setBusy(false);
    }
  }

  const link = issued ? shareUrl(typeof window === "undefined" ? "" : window.location.origin, issued.path) : "";

  return (
    <div className={libraryStyles.modalBackdrop} role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}>
      <section ref={dialogRef} tabIndex={-1} className={`${libraryStyles.modal} ${styles.dialog}`} role="dialog" aria-modal="true" aria-labelledby="share-title" data-testid="library-share-dialog">
        <header className={libraryStyles.modalHeading}>
          <div>
            <span>COMPARTILHAR MATERIAL</span>
            <h3 id="share-title">{resourceTitle}</h3>
            <p className={styles.note}>Gera um link ou QR para esta versão do material, sem turma. Não adiciona à Jornada nem apresenta na aula, e não revela quem abriu ou salvou.</p>
          </div>
          <DialogCloseButton label="Fechar compartilhamento" onClick={onClose} />
        </header>

        {!issued ? (
          <form onSubmit={(event) => { event.preventDefault(); void create(); }} className={styles.choices}>
            <fieldset className={styles.choices}>
              <legend>Quem recebe o link pode</legend>
              <label className={styles.choice}>
                <input type="radio" name="share-permission" value="VIEW" checked={permission === "VIEW"} onChange={() => setPermission("VIEW")} />
                <span><strong>{permissionLabel("VIEW")}</strong><small>Abre sem conta. Não salva, não registra presença, nota nem XP.</small></span>
              </label>
              <label className={styles.choice}>
                <input type="radio" name="share-permission" value="SAVE" checked={permission === "SAVE"} onChange={() => setPermission("SAVE")} />
                <span><strong>{permissionLabel("SAVE")}</strong><small>Exige conta de estudante verificada para abrir e salvar. Funciona mesmo sem matrícula em turma.</small></span>
              </label>
            </fieldset>
            <div className={styles.row}>
              <label><span>Validade</span>
                <select value={validDays} onChange={(event) => setValidDays(Number(event.target.value))}>
                  {SHARE_VALIDITY_DAYS.map((days) => <option key={days} value={days}>{days === 1 ? "1 dia" : `${days} dias`}</option>)}
                </select>
              </label>
              <label><span>Identificação (opcional)</span>
                <input value={label} maxLength={120} onChange={(event) => setLabel(event.target.value)} placeholder="Ex.: Grupo de estudos" />
              </label>
            </div>
            <div className={libraryStyles.editorActions}>
              <button type="button" onClick={onClose}>Cancelar</button>
              <button className={libraryStyles.primary} type="submit" disabled={busy} data-testid="library-share-create">{busy ? "Gerando..." : "Gerar link"}</button>
            </div>
          </form>
        ) : (
          <div className={styles.issued} data-testid="library-share-issued">
            <strong>Link criado. Ele só aparece agora: copie ou mostre o QR.</strong>
            <label><span>Link ({permissionLabel(issued.permission).toLowerCase()}, até {dateFormat.format(new Date(issued.expiresAt))})</span>
              <input readOnly value={link} onFocus={(event) => event.currentTarget.select()} data-testid="library-share-link" />
            </label>
            {/* blob: URL generated in memory for this dialog; next/image cannot optimize it. */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            {qrUrl && <img src={qrUrl} alt="QR Code do link de compartilhamento" />}
            <div className={styles.actions}>
              <button type="button" className={libraryStyles.primary} onClick={() => void copy()}>Copiar link</button>
              <button type="button" onClick={() => { setIssued(null); setQrUrl(null); }}>Gerar outro link</button>
            </div>
          </div>
        )}

        <section aria-labelledby="share-list-title">
          <h4 id="share-list-title">Links deste material</h4>
          {loadError && <p role="alert">{loadError}</p>}
          {!loadError && shares.length === 0 && <p>Nenhum link criado ainda.</p>}
          {shares.length > 0 && <ul className={styles.list} data-testid="library-share-list">
            {shares.map((share) => <li key={share.id}>
              <div>
                <strong>{share.label || permissionLabel(share.permission)}</strong>
                <small>{statusLabel(share.status)} · versão {share.revision} · até {dateFormat.format(new Date(share.expiresAt))}</small>
                <small>{share.viewCount} abertura(s) · {share.saveCount} salvamento(s)</small>
              </div>
              {share.status === "ACTIVE" && <button type="button" disabled={busy} onClick={() => void revoke(share)}>Revogar</button>}
            </li>)}
          </ul>}
        </section>
      </section>
    </div>
  );
}
