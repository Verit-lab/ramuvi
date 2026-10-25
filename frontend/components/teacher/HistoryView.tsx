import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { fetchSessionEvents, type SessionEvent } from "@/lib/session-event-api";
import { filterClassroomHistory, mergeClassroomHistory, type HistoryFilter } from "@/lib/classroom-history";
import type { ArenaData, Student } from "@/lib/types";
import { dateTime } from "./format";
import styles from "./HistoryView.module.css";

export function HistoryView({ classroomId, events, students, onReverse }: {
  classroomId: string;
  events: ArenaData["scoreEvents"];
  students: Student[];
  onReverse: (id: string) => void;
}) {
  const [filter, setFilter] = useState<HistoryFilter>("all");
  const [query, setQuery] = useState("");
  const [operational, setOperational] = useState<SessionEvent[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const requestSequence = useRef(0);

  const reload = useCallback(async () => {
    const requestId = ++requestSequence.current;
    setBusy(true);
    setError("");
    setOperational([]);
    try {
      const next = await fetchSessionEvents(classroomId);
      if (requestSequence.current === requestId) setOperational(next);
    } catch (reason) {
      if (requestSequence.current === requestId) {
        setError(reason instanceof Error ? reason.message : "Não foi possível consultar os eventos de condução.");
      }
    } finally {
      if (requestSequence.current === requestId) setBusy(false);
    }
  }, [classroomId]);

  useEffect(() => {
    void Promise.resolve().then(reload);
    return () => { requestSequence.current += 1; };
  }, [reload]);

  const studentsById = useMemo(() => new Map(students.map((student) => [student.id, student.name])), [students]);
  const entries = useMemo(() => mergeClassroomHistory(events, operational, studentsById), [events, operational, studentsById]);
  const rows = useMemo(() => filterClassroomHistory(entries, filter, query), [entries, filter, query]);
  const counts = {
    all: entries.length,
    xp: events.length,
    actions: operational.length,
  };
  const filters: { value: HistoryFilter; label: string }[] = [
    { value: "all", label: "Tudo" },
    { value: "xp", label: "XP dos alunos" },
    { value: "actions", label: "Ações e dinâmicas" },
  ];

  return (
    <section className={styles.workspace} aria-label="Histórico da turma">
      <header className="classroom-page-header">
        <div>
          <span className="eyebrow accent">HISTÓRICO DA TURMA</span>
          <h2>Histórico de atividades e XP</h2>
          <p>Uma linha do tempo para acompanhar a condução das aulas e a evolução de XP dos alunos.</p>
        </div>
        <button type="button" className="button" disabled={busy} onClick={() => { void reload(); }}>
          {busy ? "Atualizando..." : "Atualizar eventos"}
        </button>
      </header>
      <div className={styles.feed}>
        <div className={styles.toolbar}>
          <div className={styles.filters} role="group" aria-label="Filtrar eventos do histórico">
            {filters.map((option) => (
              <button key={option.value} type="button" className={filter === option.value ? styles.active : ""}
                aria-pressed={filter === option.value} onClick={() => setFilter(option.value)}>
                {option.label} <small>{counts[option.value]}</small>
              </button>
            ))}
          </div>
          <label className={styles.searchField}>
            <span className={styles.visuallyHidden}>Buscar no histórico</span>
            <input type="search" className="input" value={query} onChange={(event) => setQuery(event.target.value)}
              placeholder="Buscar aluno, dinâmica, categoria ou descrição..." />
          </label>
        </div>
        {error && <div className={styles.error} role="alert">
          Os eventos de condução não puderam ser carregados. Os lançamentos de XP continuam disponíveis. {error}
          <button type="button" className="button" onClick={() => { void reload(); }}>Tentar novamente</button>
        </div>}
        <div className={styles.feedHeader}>
          <strong>{rows.length} {rows.length === 1 ? "registro" : "registros"}</strong>
          <span>Mais recentes primeiro. Condução limitada aos 200 eventos disponíveis pela API.</span>
        </div>
        <div className={styles.list}>
          {rows.map((entry) => (
            <article className={styles.row} key={entry.key} data-history-kind={entry.kind}>
              <span className={entry.kind === "xp" ? styles.xpMarker : styles.actionMarker} aria-hidden="true" />
              <div className={styles.rowContent}>
                <strong>{entry.title}</strong>
                <p>{entry.detail}</p>
                <div className={styles.metadata}>
                  <time dateTime={entry.occurredAt}>{dateTime(entry.occurredAt)}</time>
                  <span>{entry.category}</span>
                  <span>{entry.kind === "xp" ? "XP" : "Condução"}</span>
                </div>
              </div>
              {entry.kind === "xp" ? (
                <div className={styles.scoreActions}>
                  <strong className={entry.score.points >= 0 ? styles.positive : styles.negative}>
                    {entry.score.points >= 0 ? "+" : ""}{entry.score.points} XP
                  </strong>
                  <button type="button" className={styles.reverseButton}
                    disabled={Boolean(entry.score.reversalOf || entry.score.reversed)}
                    aria-label={entry.score.reversalOf ? "Evento de reversão" : entry.score.reversed ? "Lançamento revertido" : `Reverter lançamento de ${entry.studentName}`}
                    title={entry.score.reversalOf ? "Evento de reversão" : entry.score.reversed ? "Lançamento já revertido" : "Reverter lançamento de XP"}
                    onClick={() => onReverse(entry.score.id)}>↶</button>
                </div>
              ) : <span className={styles.actionLabel}>Ação da aula</span>}
            </article>
          ))}
          {!busy && rows.length === 0 && (
            <div className={styles.empty}>{query.trim()
              ? "Nenhum evento corresponde à busca."
              : filter === "xp" ? "Ainda não há lançamentos de XP."
              : filter === "actions" ? "Ainda não há ações registradas nesta turma."
              : "Nenhum evento foi registrado nesta turma."}</div>
          )}
          {busy && rows.length === 0 && <div className={styles.empty}>Carregando histórico...</div>}
        </div>
      </div>
    </section>
  );
}
