"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  fetchStudentOidcProviders,
  fetchStudentOidcSession,
  fetchStudentClassrooms,
  claimStudentEnrollment,
  joinStudentClassroom,
  type StudentActiveClassroom,
  logoutStudentOidc,
  leaveStudentAccountMode,
  type StudentOidcProvider,
  type StudentOidcSession,
} from "@/lib/student-oidc-api";
import RamuviMark from "@/components/brand/RamuviMark";
import LegalLinks from "@/components/legal/LegalLinks";
import { BRAND, ENTRY_ROUTES } from "@/lib/brand";
import styles from "./student-account.module.css";
import { studyDeviceClaims } from "@/lib/study-api";

type Props = {
  session: StudentOidcSession | null;
  sessionUnavailable: boolean;
  signedOut: boolean;
  sessionError: string;
  oidcError: boolean;
  onBackToStudies: () => void;
  onSessionChange: (session: StudentOidcSession | null) => void;
  onLogout: () => void;
};

/** Unified access and account management on /learn; never grants study access itself. */
export default function StudentAccountAccess({ session, sessionUnavailable, signedOut, sessionError, oidcError, onBackToStudies, onSessionChange, onLogout }: Props) {
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
  const [classCode, setClassCode] = useState("");
  const [joining, setJoining] = useState(false);
  const autoJoinAttempted = useRef<string | null>(null);

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

  // A teacher link survives the Google OAuth redirect without becoming a bearer
  // authentication token. The backend still requires OIDC + CSRF and opt-in.
  useEffect(() => {
    try {
      const incoming = new URLSearchParams(window.location.search).get("classCode");
      if (incoming && /^[A-Za-z0-9_-]{8,40}$/.test(incoming)) {
        window.sessionStorage.setItem("ramuvi:pending-class-code", incoming.toUpperCase());
      }
      const pending = window.sessionStorage.getItem("ramuvi:pending-class-code");
      if (pending) setClassCode(pending);
    } catch { /* fallback: manual code entry */ }
  }, []);

  useEffect(() => {
    if (!session) return;
    try {
      const pending = window.sessionStorage.getItem("ramuvi:pending-class-code");
      if (!pending || autoJoinAttempted.current === pending) return;
      autoJoinAttempted.current = pending;
      window.sessionStorage.removeItem("ramuvi:pending-class-code");
      void joinWithCode(pending);
    } catch { /* manual code entry remains available */ }
  }, [session]); // eslint-disable-line react-hooks/exhaustive-deps

  async function joinWithCode(rawCode: string) {
    if (joining) return;
    setJoining(true);
    setError(null);
    setClaimSuccess("");
    try {
      const joined = await joinStudentClassroom(rawCode);
      setClassCode("");
      setClaimSuccess(`Você entrou na turma ${joined.classroomName}. As Jornadas publicadas já estão disponíveis.`);
      const [current, memberships] = await Promise.all([
        fetchStudentOidcSession(), fetchStudentClassrooms(),
      ]);
      onSessionChange(current);
      setClassrooms(memberships);
      window.history.replaceState({}, "", "/learn?account=1");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Não foi possível ingressar na turma.");
    } finally {
      setJoining(false);
    }
  }

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
      {!session && <Link href={ENTRY_ROUTES.landing} prefetch={false} className={styles.back}>
        <span aria-hidden="true">←</span> Voltar para a {BRAND.name}
      </Link>}
      <section className={styles.card} aria-labelledby="student-login-title">
        <p className={styles.brand}><RamuviMark size={28} /><span>{BRAND.learn}</span></p>
        <h1 id="student-login-title">{session ? "Minha conta" : "Acesse sua conta"}</h1>
        <p className={styles.description}>
          Entre com Google. Se seu e-mail já foi cadastrado pelo professor, suas turmas e pontuações serão recuperadas. Caso contrário, use um único código da turma.
        </p>

        {oidcError && <p role="alert" className={styles.error}>
          O provedor não concluiu a autenticação. Confira sua conta e tente novamente.
        </p>}
        {error && <p role="alert" className={styles.error}>{error}</p>}
        {sessionError && <p role="alert" className={styles.error}>{sessionError}</p>}
        {signedOut && <p role="status" className={styles.success}>Você saiu da sua conta com segurança.</p>}
        {sessionUnavailable && !signedOut && <p role="alert" className={styles.error}>
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
              : "Conta verificada. Entre com o código compartilhado da turma ou peça ao professor para conferir seu e-mail cadastrado."}</p>
            {session.studentId && <p><Link href="/learn" onClick={onBackToStudies}>Voltar ao meu espaço de estudo</Link></p>}
            {!session.studentId && <ol className={styles.firstAccessSteps} aria-label="Etapas do primeiro acesso">
              <li><strong>Conta conectada.</strong> Sua identidade já foi verificada.</li>
              <li><strong>Entre na turma.</strong> Se o professor já cadastrou seu e-mail, o vínculo é automático. Caso contrário, use o código da turma.</li>
              <li><strong>Comece a estudar.</strong> Após a vinculação, suas Jornadas disponíveis aparecerão no espaço de estudo.</li>
            </ol>}
            {claimSuccess && <p role="status" className={styles.success}>{claimSuccess} <Link href="/learn" onClick={onBackToStudies}>Ir para meu espaço de estudo</Link></p>}
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
                <form onSubmit={(event) => {
                  event.preventDefault();
                  void joinWithCode(classCode);
                }} className={styles.claimForm}>
                  <label htmlFor="classroom-join-code">Código da turma</label>
                  <input id="classroom-join-code" className={styles.inviteInput} type="text"
                    autoComplete="off" autoCapitalize="characters" spellCheck={false}
                    placeholder="Código compartilhado pelo professor"
                    value={classCode} onChange={(event) => setClassCode(event.target.value.toUpperCase())}
                    maxLength={40} required disabled={joining} />
                  <p className={styles.inviteHelp}>Um único código libera a turma e suas Jornadas publicadas. Não é necessário outro código para estudar.</p>
                  <button type="submit" className={styles.primary}
                    disabled={joining || !/^[A-Z0-9_-]{8,40}$/.test(classCode.trim())}>
                    {joining ? "Ingressando..." : "Entrar na turma"}
                  </button>
                </form>
                <details>
                  <summary>Tenho um convite individual do professor</summary>
                <form onSubmit={(event) => { void claimInvite(event); }} className={styles.claimForm}>
                  <label htmlFor="enrollment-invite-code">Código individual do convite</label>
                  <input id="enrollment-invite-code" className={styles.inviteInput} type="text"
                    aria-describedby="enrollment-invite-help"
                    autoComplete="off" autoCapitalize="none" spellCheck={false}
                    placeholder="Cole o código recebido do professor"
                    value={inviteCode} onChange={(event) => setInviteCode(event.target.value)}
                    maxLength={43} required disabled={claimBusy} />
                  <p id="enrollment-invite-help" className={styles.inviteHelp}>Use somente quando o professor pedir. O convite individual é diferente do código da turma.</p>
                  <button type="submit" className={styles.primary}
                    disabled={claimBusy || !/^[A-Za-z0-9_-]{43}$/.test(inviteCode.trim())}>
                    {claimBusy ? "Confirmando..." : "Vincular matrícula"}
                  </button>
                </form>
                </details>
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
          <Link href={ENTRY_ROUTES.liveJoin}>Entrar em uma aula com código</Link>
          {session?.studentId ? <Link href="/learn" onClick={onBackToStudies}>Voltar aos estudos</Link> : hasDeviceClaim && !sessionUnavailable ? <Link href="/learn/journeys">Acessar Jornadas com código</Link> : null}
        </div>
      </section>
      <LegalLinks className={styles.legal} />
    </main>
  );
}
