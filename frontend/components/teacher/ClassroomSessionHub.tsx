"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Button, DialogCloseButton, type Notify } from "@/components/ui";
import { useModalKeyboard } from "@/lib/use-modal-keyboard";
import {
  attendanceLabel,
  describeDuration,
  hubPrimaryAction,
  normalizeSessionTitle,
  suggestedSessionTitle,
  type ClassroomHub,
} from "@/lib/classroom-hub";
import {
  ActiveSessionConflictError,
  fetchClassroomHub,
  startClassroomSession,
  switchClassroomSession,
} from "@/lib/classroom-hub-api";
import styles from "./ClassroomSessionHub.module.css";

const dateFormat = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });

function formatDate(value: string | null) {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : dateFormat.format(date);
}

/**
 * v0.12.2 I1: the classroom is the durable entry point. One meeting = one ClassSession;
 * a forgotten session is only flagged, and "Encerrar e iniciar outra" always asks first.
 */
export function ClassroomSessionHub({
  classroomId,
  onOpenArena,
  refreshClassroomDomain,
  notify,
}: {
  classroomId: string;
  onOpenArena: () => void;
  refreshClassroomDomain: (classroomId?: string) => Promise<void>;
  notify: Notify;
}) {
  const [hub, setHub] = useState<ClassroomHub | null>(null);
  const [loadError, setLoadError] = useState("");
  const [busy, setBusy] = useState(false);
  const [title, setTitle] = useState(() => suggestedSessionTitle());
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [nextTitle, setNextTitle] = useState(() => suggestedSessionTitle());
  const dialogRef = useRef<HTMLElement | null>(null);

  const load = useCallback(async () => {
    try {
      setHub(await fetchClassroomHub(classroomId));
      setLoadError("");
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Não foi possível carregar a central da turma.");
    }
  }, [classroomId]);

  useEffect(() => {
    let active = true;
    void fetchClassroomHub(classroomId)
      .then((loaded) => { if (active) { setHub(loaded); setLoadError(""); } })
      .catch((error) => { if (active) setLoadError(error instanceof Error ? error.message : "Não foi possível carregar a central da turma."); });
    return () => { active = false; };
  }, [classroomId]);

  const closeConfirm = useCallback(() => setConfirmOpen(false), []);
  useModalKeyboard(confirmOpen, closeConfirm, dialogRef);

  async function openArenaAfterRefresh() {
    await refreshClassroomDomain(classroomId);
    onOpenArena();
  }

  async function handleConflict(error: unknown) {
    if (error instanceof ActiveSessionConflictError) {
      notify(error.message, "error");
      await load();
      return true;
    }
    return false;
  }

  async function start() {
    setBusy(true);
    try {
      await startClassroomSession(classroomId, normalizeSessionTitle(title));
      notify("Aula iniciada. Os alunos já podem entrar.");
      await openArenaAfterRefresh();
    } catch (error) {
      if (!(await handleConflict(error))) notify(error instanceof Error ? error.message : "Não foi possível iniciar a aula.", "error");
    } finally {
      setBusy(false);
    }
  }

  async function resume() {
    setBusy(true);
    try {
      await openArenaAfterRefresh();
    } catch (error) {
      notify(error instanceof Error ? error.message : "Não foi possível abrir a Arena.", "error");
    } finally {
      setBusy(false);
    }
  }

  async function confirmSwitch() {
    const active = hub?.activeSession;
    if (!active) return;
    setBusy(true);
    try {
      await switchClassroomSession(classroomId, active.id, normalizeSessionTitle(nextTitle));
      setConfirmOpen(false);
      notify(`“${active.title}” foi encerrada e preservada no histórico. Nova aula iniciada.`);
      await openArenaAfterRefresh();
    } catch (error) {
      setConfirmOpen(false);
      if (!(await handleConflict(error))) notify(error instanceof Error ? error.message : "Não foi possível trocar de aula.", "error");
    } finally {
      setBusy(false);
    }
  }

  if (loadError && !hub) {
    return (
      <section className={styles.hub} aria-labelledby="hub-title" data-testid="classroom-hub">
        <h3 id="hub-title">Aulas</h3>
        <p className={styles.error} role="alert">{loadError}</p>
        <div className={styles.actions}><Button onClick={() => { void load(); }}>Tentar novamente</Button></div>
      </section>
    );
  }
  if (!hub) {
    return <section className={styles.hub} aria-busy="true" data-testid="classroom-hub"><p>Carregando aulas…</p></section>;
  }

  const action = hubPrimaryAction(hub);
  const active = hub.activeSession;
  const panelClass = [
    styles.primary,
    action.kind === "resume" ? styles.primaryLive : "",
    action.kind === "stale" ? styles.primaryStale : "",
  ].filter(Boolean).join(" ");

  return (
    <section className={styles.hub} aria-labelledby="hub-title" data-testid="classroom-hub">
      <h3 id="hub-title">Aulas</h3>
      <div className={panelClass} data-testid="classroom-hub-primary" data-state={action.kind}>
        <span className={styles.kicker}>
          {action.kind === "start" && "Nenhuma aula em andamento"}
          {action.kind === "resume" && "Aula em andamento"}
          {action.kind === "stale" && "Aula aberta há muito tempo"}
          {action.kind === "disabled" && "Turma inativa"}
        </span>
        {active && <p className={styles.title}>{active.title}</p>}
        <p className={styles.hint} role={action.kind === "stale" ? "status" : undefined}>{action.hint}</p>
        {active && <p className={styles.hint}>{attendanceLabel(active)} · {active.connectedCount} conectado(s) agora</p>}

        {action.kind === "start" && (
          <form className={styles.startRow} onSubmit={(event) => { event.preventDefault(); void start(); }}>
            <label>
              <span>Título da aula</span>
              <input value={title} maxLength={160} onChange={(event) => setTitle(event.target.value)} data-testid="hub-session-title" />
            </label>
            <Button type="submit" variant="primary" loading={busy} loadingLabel="Iniciando…" data-testid="hub-start">
              Iniciar aula
            </Button>
          </form>
        )}

        {(action.kind === "resume" || action.kind === "stale") && (
          <div className={styles.actions}>
            <Button variant="primary" onClick={() => { void resume(); }} disabled={busy} data-testid="hub-resume">Retomar aula</Button>
            <Button
              variant={action.kind === "stale" ? "secondary" : "ghost"}
              onClick={() => { setNextTitle(suggestedSessionTitle()); setConfirmOpen(true); }}
              disabled={busy}
              data-testid="hub-switch"
            >
              Encerrar e iniciar outra
            </Button>
          </div>
        )}
      </div>

      <dl className={styles.totals} aria-label="Resumo da turma">
        <div><dt>Alunos ativos</dt><dd>{hub.totals.activeStudents}</dd></div>
        <div><dt>Jornadas publicadas</dt><dd>{hub.totals.publishedJourneys}</dd></div>
        <div><dt>Jornadas em rascunho</dt><dd>{hub.totals.draftJourneys}</dd></div>
        <div><dt>Atividades</dt><dd>{hub.totals.activities}</dd></div>
        <div><dt>Aulas registradas</dt><dd>{hub.totals.sessions}</dd></div>
      </dl>

      <div>
        <h4>Histórico de aulas</h4>
        {hub.recentSessions.length === 0 ? (
          <p className={styles.hint}>As aulas encerradas aparecem aqui, cada uma com sua própria presença.</p>
        ) : (
          <ul className={styles.history} data-testid="hub-history">
            {hub.recentSessions.map((session) => (
              <li key={session.id}>
                <strong>{session.title}</strong>
                <span>{formatDate(session.startedAt)}{session.endedAt ? ` – ${formatDate(session.endedAt)}` : ""}</span>
                <span>{attendanceLabel(session)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {confirmOpen && active && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) closeConfirm(); }}>
          <section ref={dialogRef} tabIndex={-1} className="modal-card" role="dialog" aria-modal="true" aria-labelledby="hub-switch-title" aria-describedby="hub-switch-description" data-testid="hub-switch-dialog">
            <header className="modal-header">
              <div><h2 id="hub-switch-title">Encerrar “{active.title}” e iniciar outra aula?</h2></div>
              <DialogCloseButton onClick={closeConfirm} label="Cancelar troca de aula" />
            </header>
            <p id="hub-switch-description">
              A aula atual começou {describeDuration(active.activeMinutes)}. Ela será encerrada e ficará no histórico com presença,
              dinâmicas e XP preservados; o código projetado deixa de funcionar. Jornadas, matrículas e progresso não mudam.
            </p>
            <label className={styles.dialogField}>
              <span>Título da nova aula</span>
              <input value={nextTitle} maxLength={160} onChange={(event) => setNextTitle(event.target.value)} />
            </label>
            <div className={styles.actions}>
              <Button onClick={closeConfirm} disabled={busy}>Cancelar</Button>
              <Button variant="danger" onClick={() => { void confirmSwitch(); }} loading={busy} loadingLabel="Trocando…" data-testid="hub-switch-confirm">
                Encerrar e iniciar
              </Button>
            </div>
          </section>
        </div>
      )}
    </section>
  );
}
