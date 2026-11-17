"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  fetchStudentOidcProviders,
  fetchStudentOidcSession,
  fetchStudentClassrooms,
  claimStudentEnrollment,
  type StudentActiveClassroom,
  logoutStudentOidc,
  type StudentOidcProvider,
  type StudentOidcSession,
} from "../../../lib/student-oidc-api";
import styles from "./page.module.css";

/** Small I1 access surface; redesign and classroom onboarding belong to I3. */
export default function StudentLoginPage() {
  const [providers, setProviders] = useState<StudentOidcProvider[]>([]);
  const [session, setSession] = useState<StudentOidcSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [oidcError, setOidcError] = useState(false);
  const [classrooms, setClassrooms] = useState<StudentActiveClassroom[] | null>(null);
  const [classroomsLoading, setClassroomsLoading] = useState(false);
  const [inviteCode, setInviteCode] = useState("");
  const [claimBusy, setClaimBusy] = useState(false);
  const [claimSuccess, setClaimSuccess] = useState("");

  useEffect(() => {
    setOidcError(new URLSearchParams(window.location.search).get("error") === "oidc");
    Promise.all([fetchStudentOidcProviders(), fetchStudentOidcSession()])
      .then(([available, current]) => {
        setProviders(available);
        setSession(current);
        if (current) {
          setClassroomsLoading(true);
          void fetchStudentClassrooms()
            .then(setClassrooms)
            .catch(() => setError("Não foi possível consultar suas turmas."))
            .finally(() => setClassroomsLoading(false));
        }
      })
      .catch(() => setError("Não foi possível carregar as opções de acesso."))
      .finally(() => setLoading(false));
  }, []);

  async function claimInvite(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (claimBusy) return;
    setClaimBusy(true);
    setError(null);
    setClaimSuccess("");
    try {
      const redeemed = await claimStudentEnrollment(inviteCode.trim());
      setInviteCode(""); // Do not retain the bearer invitation after redemption.
      setClaimSuccess(`Convite confirmado para a turma ${redeemed.classroomName}.`);
      const [current, memberships] = await Promise.all([
        fetchStudentOidcSession(),
        fetchStudentClassrooms(),
      ]);
      setSession(current);
      setClassrooms(memberships);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Não foi possível vincular a matrícula.");
    } finally {
      setClaimBusy(false);
    }
  }

  async function leave() {
    setBusy(true);
    setError(null);
    try {
      await logoutStudentOidc();
      setSession(null);
      setClassrooms(null);
      setInviteCode("");
      setClaimSuccess("");
    } catch {
      setError("Não foi possível sair. Tente novamente.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className={styles.shell}>
      <section className={styles.card} aria-labelledby="student-login-title">
        <div className={styles.brand}>ramuvi</div>
        <p className={styles.eyebrow}>Área do aluno</p>
        <h1 id="student-login-title">Acesse sua conta</h1>
        <p className={styles.description}>
          Entre com uma conta verificada. Seu acesso às turmas será associado por convite do professor.
        </p>

        {oidcError && <p role="alert" className={styles.error}>
          O provedor não concluiu a autenticação. Confira sua conta e tente novamente.
        </p>}
        {error && <p role="alert" className={styles.error}>{error}</p>}

        {loading ? <p role="status">Verificando opções de acesso...</p> : session ? (
          <div className={styles.status}>
            <strong>Conta autenticada</strong>
            <p>{session.studentId
              ? "Sua identidade está vinculada a um aluno. Confira as turmas associadas abaixo."
              : "Sua identidade foi verificada. Para associá-la ao seu cadastro, peça um convite individual ao professor."}</p>
            {claimSuccess && <p role="status" className={styles.success}>{claimSuccess}</p>}
            {classroomsLoading ? <p role="status">Carregando turmas...</p> : classrooms !== null ? (
              <>
                <h2 className={styles.subheading}>Minhas turmas</h2>
                {classrooms.length ? (
                  <ul className={styles.classroomList}>
                    {classrooms.map((classroom) => (
                      <li key={classroom.enrollmentId}>
                        <strong>{classroom.classroomName}</strong>
                        <small>{classroom.studentStudyAccessEnabled
                          ? "Acesso à Jornada habilitado pelo professor"
                          : "Jornada ainda não liberada pelo professor"}</small>
                      </li>
                    ))}
                  </ul>
                ) : <p>Nenhuma turma ativa foi encontrada para esta conta.</p>}
                <form onSubmit={(event) => { void claimInvite(event); }} className={styles.claimForm}>
                  <label htmlFor="enrollment-invite-code">Código individual do convite</label>
                  <input id="enrollment-invite-code" className={styles.inviteInput} type="text"
                    autoComplete="off" autoCapitalize="none" spellCheck={false}
                    placeholder="Cole o código recebido do professor"
                    value={inviteCode} onChange={(event) => setInviteCode(event.target.value)}
                    maxLength={43} required disabled={claimBusy} />
                  <button type="submit" className={styles.primary}
                    disabled={claimBusy || !/^[A-Za-z0-9_-]{43}$/.test(inviteCode.trim())}>
                    {claimBusy ? "Confirmando..." : "Vincular matrícula"}
                  </button>
                </form>
              </>
            ) : <p>O vínculo de matrícula ainda não foi habilitado nesta instalação.</p>}
            <button type="button" className={styles.secondary} onClick={leave} disabled={busy}>
              {busy ? "Encerrando..." : "Sair desta conta"}
            </button>
          </div>
        ) : providers.length ? (
          <nav aria-label="Provedores de identidade" className={styles.actions}>
            {providers.map((provider) => (
              <a className={styles.primary} href={provider.path} key={provider.id}>
                Continuar com {provider.name}
              </a>
            ))}
          </nav>
        ) : (
          <p className={styles.notice} role="status">
            O acesso por conta ainda não foi habilitado nesta instalação. Você pode participar de uma aula com o código informado pelo professor.
          </p>
        )}

        <div className={styles.footer}>
          <Link href="/join">Entrar em uma aula com código</Link>
          <Link href="/learn">Voltar à Jornada</Link>
        </div>
      </section>
    </main>
  );
}
