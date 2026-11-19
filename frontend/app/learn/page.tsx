"use client";

import Link from "next/link";
import { Suspense, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import StudyShell from "@/components/study/StudyShell";
import StudentAccountAccess from "@/components/student/StudentAccountAccess";
import { fetchStudentOidcSession, StudentAccountSessionUnavailable, studentAccountWasSignedOut, type StudentOidcSession } from "@/lib/student-oidc-api";
import { fetchAllStudyJourneys, fetchAllStudyLiveSessions, type LiveSessionSummary, type MultiClassroomJourneySummary } from "@/lib/study-api";
import styles from "@/components/study/study.module.css";

function learnerDescription(description: string | null | undefined) {
  if (!description) return "";
  const normalized = description.trim().toLowerCase();
  if ([
    "estrutura preservada a partir do markdown fornecido.",
    "estrutura preservada a partir do texto fornecido.",
    "estrutura preservada a partir do notion fornecido.",
  ].includes(normalized)) return "";
  return description.trim();
}


function StudyDashboard() {
  const [journeys, setJourneys] = useState<MultiClassroomJourneySummary[]>([]);
  const [liveSessions, setLiveSessions] = useState<LiveSessionSummary[]>([]);
  const [liveLoading, setLiveLoading] = useState(true);
  const [liveError, setLiveError] = useState(false);
  const [liveRetry, setLiveRetry] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [journeyRetry, setJourneyRetry] = useState(0);

  useEffect(() => {
    let active = true;
    void fetchAllStudyJourneys()
      .then((loaded) => { if (active) setJourneys(loaded); })
      .catch((caught) => { if (active) setError(caught instanceof Error ? caught.message : "Não foi possível recuperar seu espaço de estudo."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [journeyRetry]);

  function retryJourneys() {
    setLoading(true);
    setError("");
    setJourneyRetry((value) => value + 1);
  }

  function retryLive() {
    setLiveLoading(true);
    setLiveError(false);
    setLiveRetry((value) => value + 1);
  }

  // A live class is the most urgent thing on a student's phone: load it on its
  // own (a journeys failure must not hide it) and refresh when the tab returns.
  useEffect(() => {
    let active = true;
    let request = 0;
    const refreshLive = () => {
      if (document.visibilityState !== "visible") return;
      const current = ++request;
      void fetchAllStudyLiveSessions()
        .then((sessions) => { if (active && current === request) setLiveSessions(sessions); })
        .catch(() => { if (active && current === request) setLiveError(true); })
        .finally(() => { if (active && current === request) setLiveLoading(false); });
    };
    refreshLive();
    const onVisible = () => {
      if (document.visibilityState !== "visible") return;
      setLiveLoading(true);
      setLiveError(false);
      refreshLive();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => { active = false; document.removeEventListener("visibilitychange", onVisible); };
  }, [liveRetry]);

  const current = useMemo(() => journeys.find((journey) => journey.started && journey.progress.percent < 100)
    ?? journeys.find((journey) => journey.progress.percent < 100)
    ?? null, [journeys]);
  const others = current ? journeys.filter((journey) => journey.id !== current.id) : journeys;

  return <>
    <p className={styles.eyebrow}>Seu espaço de estudo</p>
    <h1 className={styles.title}>{journeys.length ? "Continue de onde parou." : "Seu espaço de estudo"}</h1>
    <p className={styles.lead}>Retome seus estudos, acompanhe o progresso e acesse rapidamente o próximo conteúdo.</p>

    {liveLoading && <p className={styles.liveLoadStatus} role="status">Consultando aulas ao vivo...</p>}
    {!liveLoading && liveError && <div className={styles.liveLoadError} role="alert">
      <span>Não foi possível consultar suas aulas ao vivo. Confira sua conexão.</span>
      <button type="button" onClick={retryLive}>Tentar novamente</button>
    </div>}
    {!liveLoading && !liveError && liveSessions.length > 0 && <section className={styles.homeLive} aria-label="Aula ao vivo">
      <div><span>AGORA</span><strong>{liveSessions.length === 1 ? liveSessions[0].classroomName : liveSessions.length + " aulas acontecendo"}</strong><p>{liveSessions.length === 1 ? liveSessions[0].sessionTitle : "Escolha a aula que deseja acompanhar."}</p></div>
      <Link href="/learn/live" className={styles.button}>Entrar ao vivo</Link>
    </section>}
    {loading && <div className={`${styles.hero} ${styles.skeleton}`}><h2>Carregando seus estudos…</h2><p className={styles.muted}>Recuperando jornadas e progresso.</p></div>}
    {!loading && error && <div className={styles.error} role="alert">
      <strong>Não foi possível abrir seu espaço de estudo.</strong>
      <p>{error}</p>
      <button type="button" className={styles.button} onClick={retryJourneys}>Tentar novamente</button>
      <Link href="/join" className={styles.errorSecondary}>Entrar em uma aula com código</Link>
    </div>}
    {!loading && !error && !current && journeys.length === 0 && <div className={styles.empty}><strong>Seu espaço de estudo ainda está vazio.</strong><p>Quando uma jornada for publicada em uma das suas turmas, ela aparecerá aqui.</p></div>}
    {!loading && !error && !current && journeys.length > 0 && <div className={styles.empty}><strong>Você está em dia.</strong><p>Todas as jornadas disponíveis foram concluídas. <Link href="/learn/journeys">Ver jornadas concluídas</Link></p></div>}


    {current && <section className={styles.hero} aria-labelledby="continue-title">
      <div className={styles.heroTop}><div><p className={styles.eyebrow}>Jornada em andamento</p><h2 id="continue-title">{current.title}</h2>{learnerDescription(current.description) && <p className={styles.muted}>{learnerDescription(current.description)}</p>}{current.currentContentTitle && <p className={styles.current}>Próximo conteúdo<strong>{current.currentContentTitle}</strong></p>}</div>
      <Link className={styles.button} href={current.currentContentId ? `/learn/journeys/${current.id}/content/${current.currentContentId}` : `/learn/journeys/${current.id}`}>{current.started ? "Continuar" : "Começar"}</Link></div>
      <div className={styles.progress} aria-label={`${current.progress.percent}% concluído`}><span style={{ width: `${current.progress.percent}%` }} /></div>
      <div className={styles.progressMeta}><span>{current.progress.completedContents} de {current.progress.totalContents} conteúdos</span><strong>{current.progress.percent}%</strong></div>
    </section>}

    {!loading && !error && others.filter(j=>j.progress.percent<100).length > 0 && <section className={styles.section}><div className={styles.sectionHead}><h2>Em andamento</h2><Link href="/learn/journeys" className={styles.sectionLink}>Ver todas</Link></div><div className={styles.list}>{others.filter(j=>j.progress.percent<100).slice(0,3).map((journey) => <Link key={journey.id} href={`/learn/journeys/${journey.id}`} className={styles.card}><div><h3>{journey.title}</h3><p>{journey.progress.percent === 100 ? "Jornada concluída" : journey.currentContentTitle ? `${journey.started ? "Continuar" : "Começar"} em ${journey.currentContentTitle}` : journey.description || "Jornada de estudo"}</p></div><span className={styles.percent}>{journey.progress.percent}%</span></Link>)}</div></section>}
  </>;
}


/** /learn is the only student account entry; the study dashboard stays at the same URL. */
function LearnEntry() {
  const searchParams = useSearchParams();
  const [status, setStatus] = useState<"checking" | "guest" | "authenticated">("checking");
  const [session, setSession] = useState<StudentOidcSession | null>(null);
  const [sessionUnavailable, setSessionUnavailable] = useState(false);
  const [signedOut, setSignedOut] = useState(false);
  const [sessionError, setSessionError] = useState("");
  const [showAccountAfterClaim, setShowAccountAfterClaim] = useState(false);

  useEffect(() => {
    let active = true;
    void fetchStudentOidcSession()
      .then((current) => {
        if (!active) return;
        setSession(current);
        setSessionUnavailable(false);
        setSignedOut(false);
        setStatus(current ? "authenticated" : "guest");
      })
      .catch((error: unknown) => {
        if (!active) return;
        setSessionUnavailable(error instanceof StudentAccountSessionUnavailable);
        setSignedOut(error instanceof StudentAccountSessionUnavailable && studentAccountWasSignedOut());
        if (!(error instanceof StudentAccountSessionUnavailable)) setSessionError("Não foi possível verificar a sessão. Confira sua conexão e tente novamente.");
        setStatus("guest"); // Never silently fall back to a remembered device token.
      });
    return () => { active = false; };
  }, []);

  if (status === "checking") {
    return <main role="status" aria-live="polite" className={styles.entryLoading}>Verificando seu acesso...</main>;
  }

  const accountRequested = searchParams.get("account") === "1" || searchParams.get("connected") === "1";
  if (status === "guest" || accountRequested || !session?.studentId || showAccountAfterClaim) {
    return <StudentAccountAccess
      session={session}
      sessionUnavailable={sessionUnavailable}
      signedOut={signedOut}
      sessionError={sessionError}
      oidcError={searchParams.get("error") === "oidc"}
      onBackToStudies={() => setShowAccountAfterClaim(false)}
      onSessionChange={(updated) => {
        setSession(updated);
        setSignedOut(false);
        setStatus(updated ? "authenticated" : "guest");
        setShowAccountAfterClaim(true);
      }}
      onLogout={() => {
        setSession(null);
        setSessionError("");
        setSessionUnavailable(true); // Keep device-token fallback blocked until explicit selection.
        setSignedOut(true);
        setStatus("guest");
      }}
    />;
  }

  return <StudyShell><StudyDashboard /></StudyShell>;
}

export default function LearnHomePage() {
  return <Suspense fallback={<main role="status">Verificando seu acesso...</main>}><LearnEntry /></Suspense>;
}
