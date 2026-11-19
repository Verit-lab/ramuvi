"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  fetchStudentOidcProviders,
  fetchStudentOidcSession,
  fetchStudentClassrooms,
  claimStudentEnrollment,
  acceptClassroomLink,
  captureJoinLinkFromLocation,
  clearPendingJoinLink,
  ClassroomLinkError,
  fetchOwnJoinRequests,
  joinResultMessage,
  normalizeJoinLinkToken,
  previewClassroomLink,
  type ClassroomLinkPreview,
  type OwnJoinRequest,
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
  // ADR-0074: a join link is shown for explicit confirmation; never accepted silently.
  const [pendingLink, setPendingLink] = useState<string | null>(null);
  const [linkPreview, setLinkPreview] = useState<ClassroomLinkPreview | null>(null);
  const [linkError, setLinkError] = useState<{ message: string; retryable: boolean } | null>(null);
  const [linkBusy, setLinkBusy] = useState(false);
  const [manualLink, setManualLink] = useState("");
  const [ownRequests, setOwnRequests] = useState<OwnJoinRequest[]>([]);

  useEffect(() => {
    void fetchStudentOidcProviders()
      .then(setProviders)
      .catch(() => setError("Não foi possível consultar os provedores de acesso."))
      .finally(() => setLoading(false));
    setPendingLink(captureJoinLinkFromLocation());
  }, []);

  useEffect(() => {
    if (!session) return;
    let active = true;
    void fetchStudentClassrooms()
      .then((items) => { if (active) setClassrooms(items); })
      .catch(() => { if (active) setError("Não foi possível consultar suas turmas."); })
      .finally(() => { if (active) setClassroomsLoading(false); });
    void fetchOwnJoinRequests().then((items) => { if (active) setOwnRequests(items); });
    return () => { active = false; };
  }, [session]);

  useEffect(() => {
    if (!session || !pendingLink) return;
    void loadPreview(pendingLink);
  }, [session, pendingLink]); // eslint-disable-line react-hooks/exhaustive-deps

  function linkFailure(caught: unknown) {
    setLinkError(caught instanceof ClassroomLinkError
      ? { message: caught.message, retryable: caught.retryable }
      : { message: "Não foi possível usar o link de ingresso.", retryable: true });
  }

  async function loadPreview(token: string) {
    setLinkBusy(true);
    setLinkError(null);
    setLinkPreview(null);
    try {
      setLinkPreview(await previewClassroomLink(token));
    } catch (caught) {
      linkFailure(caught);
    } finally {
      setLinkBusy(false);
    }
  }

  function discardLink() {
    clearPendingJoinLink();
    setPendingLink(null);
    setLinkPreview(null);
    setLinkError(null);
  }

  async function confirmJoin() {
    if (!pendingLink || linkBusy) return;
    setLinkBusy(true);
    setLinkError(null);
    setClaimSuccess("");
    try {
      const result = await acceptClassroomLink(pendingLink);
      discardLink();
      setClaimSuccess(joinResultMessage(result));
      const [current, memberships, requests] = await Promise.all([
        fetchStudentOidcSession(), fetchStudentClassrooms(), fetchOwnJoinRequests(),
      ]);
      onSessionChange(current);
      setClassrooms(memberships);
      setOwnRequests(requests);
    } catch (caught) {
      // The pending link stays saved when a retry makes sense (network, throttling).
      linkFailure(caught);
      if (!(caught instanceof ClassroomLinkError && caught.retryable)) clearPendingJoinLink();
    } finally {
      setLinkBusy(false);
    }
  }

  function submitManualLink(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fromUrl = manualLink.includes("#ingresso=") ? manualLink.split("#ingresso=")[1] : manualLink;
    const token = normalizeJoinLinkToken(fromUrl ?? "");
    if (!token) {
      setLinkError({ message: "Cole o link completo ou o código de ingresso de 32 caracteres enviado pelo professor.", retryable: false });
      return;
    }
    setManualLink("");
    setPendingLink(token);
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
      discardLink();
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
          Entre com Google e abra o link de ingresso enviado pelo professor. Você confirma a turma antes de entrar. Seu histórico só é recuperado quando o professor já tinha seu cadastro nesta turma ou aprova o vínculo.
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
              : "Conta verificada. Abra o link de ingresso da turma ou use o convite individual enviado pelo professor."}</p>
            {session.studentId && <p><Link href="/learn" onClick={onBackToStudies}>Voltar ao meu espaço de estudo</Link></p>}
            {!session.studentId && <ol className={styles.firstAccessSteps} aria-label="Etapas do primeiro acesso">
              <li><strong>Conta conectada.</strong> Sua identidade já foi verificada.</li>
              <li><strong>Entre na turma.</strong> Abra o link de ingresso e confirme a turma. Em alguns casos o professor precisa aprovar.</li>
              <li><strong>Comece a estudar.</strong> As Jornadas aparecem quando o professor libera o acesso a estudos da turma.</li>
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
                {ownRequests.filter((item) => item.status !== "APPROVED").length > 0 && <>
                  <h2 className={styles.subheading}>Pedidos de ingresso</h2>
                  <ul className={styles.classroomList}>
                    {ownRequests.filter((item) => item.status !== "APPROVED").map((item) => (
                      <li key={item.requestId}>
                        <strong>{item.classroomName}</strong>
                        <small>{item.status === "PENDING"
                          ? "Aguardando o professor. Nada foi vinculado ainda."
                          : "Recusado pelo professor. Fale com ele ou peça um novo link."}</small>
                      </li>
                    ))}
                  </ul>
                </>}
                {pendingLink ? (
                  <section className={styles.claimForm} aria-labelledby="join-link-title">
                    <h2 id="join-link-title" className={styles.subheading}>Link de ingresso</h2>
                    {linkBusy && !linkPreview && <p role="status">Conferindo o link...</p>}
                    {linkPreview && (linkPreview.alreadyMember
                      ? <p role="status">Você já participa da turma <strong>{linkPreview.classroomName}</strong>.</p>
                      : linkPreview.pendingRequest
                        ? <p role="status">Seu pedido para <strong>{linkPreview.classroomName}</strong> já está com o professor.</p>
                        : <p>Entrar na turma <strong>{linkPreview.classroomName}</strong> com esta conta?
                          {linkPreview.requiresApproval && " O professor precisará aprovar seu ingresso."}</p>)}
                    {linkError && <p role="alert" className={styles.error}>{linkError.message}</p>}
                    {linkPreview && !linkPreview.alreadyMember && !linkPreview.pendingRequest && (
                      <button type="button" className={styles.primary} disabled={linkBusy} onClick={() => { void confirmJoin(); }}>
                        {linkBusy ? "Entrando..." : "Confirmar e entrar na turma"}
                      </button>
                    )}
                    {linkError?.retryable && (
                      <button type="button" className={styles.secondary} disabled={linkBusy}
                        onClick={() => { void (linkPreview ? confirmJoin() : loadPreview(pendingLink)); }}>
                        Tentar novamente
                      </button>
                    )}
                    <button type="button" className={styles.secondary} disabled={linkBusy} onClick={discardLink}>
                      {linkPreview?.alreadyMember || linkPreview?.pendingRequest ? "Fechar" : "Cancelar"}
                    </button>
                  </section>
                ) : (
                  <form onSubmit={submitManualLink} className={styles.claimForm}>
                    <label htmlFor="classroom-join-link">Link ou código de ingresso da turma</label>
                    <input id="classroom-join-link" className={styles.inviteInput} type="text"
                      autoComplete="off" autoCapitalize="characters" spellCheck={false}
                      placeholder="Cole o link enviado pelo professor"
                      value={manualLink} onChange={(event) => setManualLink(event.target.value)}
                      maxLength={200} required />
                    {linkError && <p role="alert" className={styles.error}>{linkError.message}</p>}
                    <p className={styles.inviteHelp}>O código administrativo da turma não serve para entrar. Use o link de ingresso; você confirma a turma antes de entrar.</p>
                    <button type="submit" className={styles.primary} disabled={!manualLink.trim()}>Continuar</button>
                  </form>
                )}
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
                  <p id="enrollment-invite-help" className={styles.inviteHelp}>Exceção: use somente quando o professor enviar um convite só para você. Ele é diferente do link de ingresso da turma.</p>
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
