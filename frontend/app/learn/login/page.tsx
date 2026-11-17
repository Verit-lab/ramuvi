"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  fetchStudentOidcProviders,
  fetchStudentOidcSession,
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

  useEffect(() => {
    setOidcError(new URLSearchParams(window.location.search).get("error") === "oidc");
    Promise.all([fetchStudentOidcProviders(), fetchStudentOidcSession()])
      .then(([available, current]) => {
        setProviders(available);
        setSession(current);
      })
      .catch(() => setError("Não foi possível carregar as opções de acesso."))
      .finally(() => setLoading(false));
  }, []);

  async function leave() {
    setBusy(true);
    setError(null);
    try {
      await logoutStudentOidc();
      setSession(null);
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
          <div className={styles.status} role="status">
            <strong>Conta autenticada</strong>
            <p>{session.studentId
              ? "Sua conta está vinculada a um aluno. A página de turmas será liberada em uma próxima etapa."
              : "Sua identidade foi verificada. O vínculo com sua matrícula ainda depende de convite individual."}</p>
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
