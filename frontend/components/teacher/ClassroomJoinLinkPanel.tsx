import { useCallback, useEffect, useState } from "react";
import { type Notify } from "@/components/ui";
import {
  approveJoinRequest,
  fetchJoinLinkCapabilities,
  fetchJoinLinkStatus,
  fetchJoinReviewQueue,
  issueJoinLink,
  joinLinkUrl,
  rejectJoinRequest,
  revokeJoinLink,
  setJoinLinkApproval,
  type JoinLinkCapabilities,
  type JoinLinkStatus,
  type JoinRequestReason,
  type JoinReviewQueue,
} from "@/lib/classroom-join-link-api";
import { errorMessage } from "./format";

const REASONS: Record<JoinRequestReason, string> = {
  APPROVAL_REQUIRED: "Aprovação ligada no link",
  POSSIBLE_DUPLICATE: "Pode ser um aluno já cadastrado",
  EMAIL_REVIEW: "E-mail Google já conhecido: confirme o cadastro",
};

/**
 * ADR-0074. Rendered only when the backend capability is ON (same condition as the API).
 * The link URL lives only in component state right after generation; it cannot be shown
 * again later (only its hash exists), so "Gerar novo link" is the way to share it again.
 */
export function ClassroomJoinLinkPanel({ classroomId, classroomActive, notify }: {
  classroomId: string;
  classroomActive: boolean;
  notify: Notify;
}) {
  const [capabilities, setCapabilities] = useState<JoinLinkCapabilities | null>(null);
  const [status, setStatus] = useState<JoinLinkStatus | null>(null);
  const [queue, setQueue] = useState<JoinReviewQueue | null>(null);
  const [freshUrl, setFreshUrl] = useState<string | null>(null);
  const [validDays, setValidDays] = useState(7);
  const [requiresApproval, setRequiresApproval] = useState(false);
  const [choice, setChoice] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    const [current, requests] = await Promise.all([
      fetchJoinLinkStatus(classroomId), fetchJoinReviewQueue(classroomId),
    ]);
    setStatus(current);
    setQueue(requests);
  }, [classroomId]);

  useEffect(() => {
    setFreshUrl(null);
    void fetchJoinLinkCapabilities().then((value) => {
      setCapabilities(value);
      if (value.classroomJoinLinksEnabled) void refresh().catch((error) => notify(errorMessage(error), "error"));
    });
  }, [classroomId, refresh, notify]);

  if (!capabilities?.classroomJoinLinksEnabled) return null;

  async function run(action: () => Promise<void>) {
    if (busy) return;
    setBusy(true);
    try { await action(); } catch (error) { notify(errorMessage(error), "error"); } finally { setBusy(false); }
  }

  const generate = () => run(async () => {
    if (status?.active && !window.confirm("Gerar um novo link desativa o link atual. Continuar?")) return;
    const issued = await issueJoinLink(classroomId, validDays, requiresApproval);
    setFreshUrl(joinLinkUrl(window.location.origin, issued));
    await refresh();
    notify("Link de ingresso gerado. Copie agora: por segurança ele não poderá ser exibido de novo.");
  });

  const copy = async () => {
    if (!freshUrl) return;
    try {
      await navigator.clipboard.writeText(freshUrl);
      notify("Link copiado. Os alunos entram com Google e confirmam a turma.");
    } catch {
      notify("Não foi possível copiar automaticamente. Selecione o link e copie manualmente.", "error");
    }
  };

  const revoke = () => run(async () => {
    await revokeJoinLink(classroomId);
    setFreshUrl(null);
    await refresh();
    notify("Link revogado. Matrículas e pedidos existentes foram preservados.");
  });

  const toggleApproval = () => run(async () => {
    setStatus(await setJoinLinkApproval(classroomId, !(status?.requiresApproval ?? false)));
  });

  /** Default selection: the student whose teacher-entered e-mail matched, else a new record. */
  const approve = (requestId: string, suggested: string | null) => run(async () => {
    const selected = choice[requestId] ?? suggested ?? "";
    await approveJoinRequest(classroomId, requestId, selected
      ? { mode: "LINK_EXISTING", studentId: selected }
      : { mode: "CREATE_NEW" });
    await refresh();
    notify(selected ? "Conta vinculada ao cadastro existente; histórico preservado." : "Aluno aprovado com cadastro novo.");
  });

  const reject = (requestId: string) => run(async () => {
    await rejectJoinRequest(classroomId, requestId);
    await refresh();
    notify("Pedido recusado.");
  });

  const formatDate = (value: string | null) => value ? new Date(value).toLocaleString("pt-BR") : "—";

  return (
    <section className="classroom-settings-card" aria-labelledby="join-link-heading">
      <div className="classroom-settings-section-heading">
        <span className="eyebrow accent">PRIMEIRO ACESSO</span>
        <h3 id="join-link-heading">Link de ingresso da turma</h3>
        <p>Os alunos abrem o link, entram com Google e confirmam a turma. O código administrativo da turma não dá acesso.
          O link expira, pode ser revogado e não libera Jornadas sozinho: isso depende do acesso a estudos abaixo.
          {capabilities.autoEmailLinkEnabled
            ? " Alunos que você já cadastrou nesta turma com o mesmo e-mail Google verificado recuperam o histórico automaticamente."
            : " Alunos já cadastrados chegam como pedido para você confirmar o vínculo."}</p>
      </div>

      {status?.active ? (
        <p role="status">{status.expired ? "Link expirado" : "Link ativo"} · válido até {formatDate(status.expiresAt)} ·
          {" "}{status.acceptedCount} ingresso(s){status.requiresApproval ? " · com aprovação" : ""}</p>
      ) : <p role="status">Nenhum link ativo. Convites individuais continuam disponíveis como exceção.</p>}

      {freshUrl && (
        <div className="stack-sm">
          <label htmlFor="fresh-join-link"><strong>Novo link (mostrado só agora)</strong></label>
          <input id="fresh-join-link" className="input" readOnly value={freshUrl} onFocus={(event) => event.target.select()} />
          <button type="button" className="button secondary" onClick={() => { void copy(); }}>Copiar link para alunos</button>
        </div>
      )}

      <div className="stack-sm">
        <label>Validade
          <select className="select" value={validDays} disabled={busy} onChange={(event) => setValidDays(Number(event.target.value))}>
            {[1, 3, 7, 14, 30].map((days) => <option key={days} value={days}>{days} dia(s)</option>)}
          </select>
        </label>
        <label>
          <input type="checkbox" checked={requiresApproval} disabled={busy} onChange={(event) => setRequiresApproval(event.target.checked)} />
          {" "}Exigir minha aprovação para alunos novos
        </label>
        <button type="button" className="button" disabled={busy || !classroomActive} onClick={() => { void generate(); }}>
          {status?.active ? "Gerar novo link (desativa o atual)" : "Gerar link de ingresso"}
        </button>
        {status?.active && <>
          <button type="button" className="button secondary" disabled={busy} onClick={() => { void toggleApproval(); }}>
            {status.requiresApproval ? "Dispensar aprovação neste link" : "Exigir aprovação neste link"}
          </button>
          <button type="button" className="button secondary" disabled={busy} onClick={() => { void revoke(); }}>Revogar link</button>
        </>}
      </div>

      <h4>Pedidos de ingresso ({queue?.requests.length ?? 0})</h4>
      {queue && queue.requests.length === 0 && <p>Nenhum pedido aguardando.</p>}
      {queue && queue.requests.length > 0 && (
        <ul className="stack-sm" aria-label="Pedidos de ingresso">
          {queue.requests.map((request) => (
            <li key={request.requestId} className="stack-sm">
              <strong>{request.displayName}</strong>
              <small>{REASONS[request.reason]}{request.verifiedEmail ? ` · ${request.verifiedEmail}` : ""} · {formatDate(request.createdAt)}</small>
              {!request.accountAlreadyLinked && (
                <label>Vincular a
                  <select className="select" value={choice[request.requestId] ?? request.emailMatchStudentId ?? ""}
                    onChange={(event) => setChoice({ ...choice, [request.requestId]: event.target.value })}>
                    <option value="">Cadastro novo</option>
                    {request.emailMatchStudentId && !queue.candidates.some((item) => item.studentId === request.emailMatchStudentId) &&
                      <option value={request.emailMatchStudentId}>Cadastro com este e-mail (outra turma)</option>}
                    {queue.candidates.map((candidate) => (
                      <option key={candidate.studentId} value={candidate.studentId}>
                        {candidate.preferredName || candidate.name}{candidate.registration ? ` (${candidate.registration})` : ""}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              <div>
                <button type="button" className="button" disabled={busy}
                  onClick={() => { void approve(request.requestId, request.emailMatchStudentId); }}>Aprovar</button>{" "}
                <button type="button" className="button secondary" disabled={busy} onClick={() => { void reject(request.requestId); }}>Recusar</button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
