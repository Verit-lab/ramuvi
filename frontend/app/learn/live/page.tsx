"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import StudentJoinFlow from "@/components/StudentJoinFlow";
import { fetchAllStudyLiveSessions, studyDeviceClaims, type LiveSessionSummary } from "@/lib/study-api";
import styles from "@/components/study/study.module.css";

export default function LivePage() {
  const [sessions, setSessions] = useState<LiveSessionSummary[]>([]);
  const [selectedCode, setSelectedCode] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [retry, setRetry] = useState(0);
  const [hasRememberedClassroom, setHasRememberedClassroom] = useState(false);

  function refresh() {
    setLoading(true);
    setLoadError(false);
    setRetry((value) => value + 1);
  }

  useEffect(() => {
    let active = true;
    void fetchAllStudyLiveSessions()
      .then((items) => {
        if (active) {
          setSessions(items);
          setHasRememberedClassroom(studyDeviceClaims().length > 0);
        }
      })
      .catch(() => { if (active) setLoadError(true); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [retry]);

  if (selectedCode) return <>
    <button type="button" className={styles.liveBack} onClick={() => { setSelectedCode(""); refresh(); }}>← Voltar para aulas ao vivo</button>
    <div className={styles.embeddedJoin}><StudentJoinFlow embedded initialCode={selectedCode} /></div>
  </>;

  return <>
    <p className={styles.eyebrow}>Arena ao vivo</p>
    <h1 className={styles.title}>Entrar ao vivo</h1>
    <p className={styles.lead}>Participe das aulas e atividades conduzidas pelo professor. Sessões das suas turmas aparecem aqui automaticamente.</p>

    {loading && <p role="status" className={styles.liveLoadStatus}>Consultando aulas ao vivo...</p>}
    {!loading && loadError && <div role="alert" className={styles.liveLoadError}>
      <span>Não foi possível consultar as aulas ao vivo. Verifique sua conexão.</span>
      <button type="button" onClick={refresh}>Tentar novamente</button>
    </div>}

    {!loading && !loadError && sessions.length > 0 && <section className={styles.liveNow} aria-labelledby="live-now-title">
      <div className={styles.liveSectionHead}><div><span>ACONTECENDO AGORA</span><h2 id="live-now-title">{sessions.length === 1 ? "Sua aula está ao vivo" : "Aulas disponíveis agora"}</h2></div><button type="button" onClick={refresh}>Atualizar</button></div>
      <div className={styles.liveSessionList}>{sessions.map((session) =>
        <article className={styles.liveSessionCard} key={session.sessionId}>
          <div><span>{session.classroomName}</span><strong>{session.sessionTitle}</strong><small>Aula em andamento</small></div>
          <button type="button" onClick={() => setSelectedCode(session.joinCode)}>Entrar na aula</button>
        </article>
      )}</div>
    </section>}

    {!loading && !loadError && hasRememberedClassroom && sessions.length === 0 && (
      <section className={styles.liveRemembered} aria-label="Turmas lembradas neste dispositivo">
        <strong>Nenhuma aula ao vivo encontrada neste momento.</strong>
        <p>Se o vínculo salvo ainda estiver válido, você poderá continuar as Jornadas. Caso tenha expirado, entre novamente com um código.</p>
        <Link href="/learn/journeys">Ver minhas Jornadas</Link>
      </section>
    )}

    <section className={styles.liveManual}>
      {(sessions.length > 0 || hasRememberedClassroom) && <div className={styles.liveDivider}><span>OUTRA AULA? ENTRE COM CÓDIGO</span></div>}
      <div className={styles.embeddedJoin}><StudentJoinFlow embedded /></div>
    </section>
  </>;
}
