"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  fetchStudentOidcProviders,
  fetchStudentOidcSession,
  fetchStudentClassrooms,
  claimStudentEnrollment,
  type StudentActiveClassroom,
  logoutStudentOidc,
  leaveStudentAccountMode,
  type StudentOidcProvider,
  type StudentOidcSession,
} from "@/lib/student-oidc-api";
import styles from "./student-account.module.css";
import { studyDeviceClaims } from "@/lib/study-api";

type Props = {
  session: StudentOidcSession | null;
  sessionUnavailable: boolean;
  sessionError: string;
  oidcError: boolean;
  onBackToStudies: () => void;
  onSessionChange: (session: StudentOidcSession | null) => void;
  onLogout: () => void;
};

/** Unified access and account management on /learn; never grants study access itself. */
export default function StudentAccountAccess({ session, sessionUnavailable, sessionError, oidcError, onBackToStudies, onSessionChange, onLogout }: Props) {
  const router = useRouter();
  const hasDeviceClaim = studyDeviceClaims().length > 0;
  const [providers, setProviders] = useState<StudentOidcProvider[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [classrooms, setClassrooms] = useState<StudentActiveClassroom[] | null>(null);
  const [classroomsLoading, setClassroomsLoading] = useState(Boolean(session));
  const [inviteCode, setInviteCode] = useState("");
  const [claimBusy, setClaimBusy] = useState(false);
  const [claimSuccess, setClaimSuccess] = useState("");

  useEffect(() => {
    void fetchStudentOidcProviders()
      .then(setProviders)
      .catch(() => setError("Não foi possível consultar os provedores de acesso."))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!session) return;
    let active = true;
    void fetchStudentClassrooms()
      .then((items) => { if (active) setClassrooms(items); })
      .catch(() => { if (active) setError("Não foi possível consultar suas turmas."); })
      .finally(() => { if (active) setClassroomsLoading(false); });
    return () => { active = false; };
  }, [session]);

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
      onSessionChange(current);
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
      onLogout();
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
        <p className={styles.eyebrow}>Ramuvi Learn · Área do aluno</p>
        <h1 id="student-login-title">{session ? "Minha conta" : "Acesse sua conta"}</h1>
        <p className={styles.description}>
          Entre com uma conta verificada. Seu acesso às turmas será associado por convite do professor.
        </p>

        {oidcError && <p role="alert" className={styles.error}>
          O provedor não concluiu a autenticação. Confira sua conta e tente novamente.
        </p>}
        {error && <p role="alert" className={styles.error}>{error}</p>}
        {sessionError && <p role="alert" className={styles.error}>{sessionError}</p>}
        {sessionUnavailable && <p role="alert" className={styles.error}>
          Sua sessão de conta foi encerrada ou revogada. Entre novamente ou escolha explicitamente o acesso por código.
        </p>}
        {sessionUnavailable && (
          <button type="button" className={styles.secondary} onClick={() => {
            leaveStudentAccountMode();
            router.push("/learn/journeys");
          }}>
            Continuar explicitamente com acesso por código
          </button>
        )}

        {session ? (
          <div className={styles.status}>
            <strong>Conta autenticada</strong>
            <p>{session.studentId
              ? "Sua identidade está vinculada a um aluno. Confira as turmas associadas abaixo."
              : "Sua identidade foi verificada. Para associá-la ao seu cadastro, peça um convite individual ao professor."}</p>
            {session.studentId && <p><Link href="/learn" onClick={onBackToStudies}>Voltar ao meu espaço de estudo</Link></p>}
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
        ) : loading ? (
          <p role="status">Consultando opções de acesso...</p>
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
          {session?.studentId ? <Link href="/learn" onClick={onBackToStudies}>Voltar aos estudos</Link> : hasDeviceClaim && !sessionUnavailable ? <Link href="/learn/journeys">Acessar Jornadas com código</Link> : null}
          <Link href="/termos">Termos de Uso</Link>
          <Link href="/privacidade">Política de Privacidade</Link>
        </div>
      </section>
    </main>
  );
}
