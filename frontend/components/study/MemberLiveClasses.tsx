"use client";

import { useEffect, useState } from "react";
import { fetchStudentCapabilities } from "@/lib/student-capabilities-api";
import { fetchMemberLiveClasses, joinMemberLiveClass, type MemberLiveClass } from "@/lib/member-live-api";
import styles from "./study.module.css";

/**
 * v0.12.2 I2 (ADR-0076 D4): live classes of the verified member's classrooms. Entering never asks
 * for the projected code; the backend re-checks account, enrollment, classroom and session.
 * Renders nothing while the switch is OFF, so the v0.12.1 flow stays unchanged.
 */
export default function MemberLiveClasses() {
  const [enabled, setEnabled] = useState(false);
  const [classes, setClasses] = useState<MemberLiveClass[]>([]);
  const [error, setError] = useState("");
  const [joining, setJoining] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    void (async () => {
      const capabilities = await fetchStudentCapabilities();
      if (!active || !capabilities.liveMemberJoin) return;
      setEnabled(true);
      try {
        const loaded = await fetchMemberLiveClasses();
        if (active) { setClasses(loaded); setError(""); }
      } catch {
        if (active) setError("Não foi possível consultar suas aulas ao vivo. Confira sua conexão.");
      }
    })();
    return () => { active = false; };
  }, [retry]);

  async function enter(sessionId: string) {
    setJoining(sessionId);
    setError("");
    try {
      window.location.assign(await joinMemberLiveClass(sessionId));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Não foi possível entrar na aula.");
      setJoining(null);
      setRetry((value) => value + 1);
    }
  }

  if (!enabled) return null;
  if (error && classes.length === 0) {
    return <div className={styles.liveLoadError} role="alert" data-testid="member-live-error">
      <span>{error}</span>
      <button type="button" onClick={() => { setError(""); setRetry((value) => value + 1); }}>Tentar novamente</button>
    </div>;
  }
  if (classes.length === 0) return null;
  return <section className={styles.homeLive} aria-label="Suas aulas ao vivo" data-testid="member-live">
    <div>
      <span>AO VIVO NAS SUAS TURMAS</span>
      {error && <p role="alert">{error}</p>}
      <ul className={styles.memberLiveList}>
        {classes.map((item) => <li key={item.sessionId}>
          <div><strong>{item.classroomName}</strong><p>{item.sessionTitle}</p></div>
          <button type="button" className={styles.button} onClick={() => { void enter(item.sessionId); }}
            disabled={joining !== null} aria-busy={joining === item.sessionId}
            data-testid="member-live-enter">
            {joining === item.sessionId ? "Entrando…" : "Entrar na aula"}
          </button>
        </li>)}
      </ul>
      <p>Você entra com sua conta; não precisa digitar o código da aula.</p>
    </div>
  </section>;
}
