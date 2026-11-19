"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import RamuviMark from "@/components/brand/RamuviMark";
import LegalLinks from "@/components/legal/LegalLinks";
import { loginTeacher, type TeacherSession } from "@/lib/auth-api";
import { BRAND, ENTRY_ROUTES } from "@/lib/brand";

type Props = {
  onAuthenticated: (session: TeacherSession) => void;
  /** "dialog": the login expired mid-use; re-authenticate over the open screen. */
  mode?: "page" | "dialog";
};

export default function TeacherLogin({ onAuthenticated, mode = "page" }: Props) {
  // No pre-filled account name: the login screen is public and must not
  // disclose which username the instance uses.
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!username.trim() || !password) return;
    setBusy(true);
    setError("");
    try {
      onAuthenticated(await loginTeacher(username.trim(), password));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível autenticar.");
    } finally {
      setBusy(false);
    }
  }

  if (mode === "dialog") {
    return (
      <div className="teacher-relogin-backdrop">
        <section className="teacher-login-card" role="dialog" aria-modal="true" aria-labelledby="teacher-relogin-title">
          <h2 id="teacher-relogin-title">Entre de novo para continuar</h2>
          <p>O acesso expirou por tempo sem uso. A tela continua aberta: depois de entrar, repita a última ação.</p>
          <form onSubmit={submit} className="teacher-login-form">
            <label>Usuário<input className="input" autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} autoFocus required /></label>
            <label>Senha<input className="input" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>
            {error && <div className="api-alert teacher-login-error" role="alert"><strong>Acesso negado.</strong><span>{error}</span></div>}
            <button className="button primary large" type="submit" disabled={busy}>{busy ? "Entrando…" : "Entrar"}</button>
          </form>
        </section>
      </div>
    );
  }

  return (
    <main className="teacher-login-shell">
      <Link href={ENTRY_ROUTES.landing} prefetch={false} className="teacher-login-back">
        <span aria-hidden="true">←</span> Voltar para a {BRAND.name}
      </Link>
      <section className="teacher-login-card" aria-labelledby="teacher-login-title">
        <p className="teacher-login-product">
          <RamuviMark size={28} />
          <span>{BRAND.teach}</span>
        </p>
        <h1 id="teacher-login-title">Acesso do professor</h1>
        <p>Entre para organizar turmas, preparar jornadas e conduzir aulas ao vivo.</p>
        <form onSubmit={submit} className="teacher-login-form">
          <label>Usuário<input className="input" autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} autoFocus required /></label>
          <label>Senha<input className="input" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>
          {error && <div className="api-alert teacher-login-error" role="alert"><strong>Acesso negado.</strong><span>{error}</span></div>}
          <button className="button primary large" type="submit" disabled={busy}>{busy ? "Entrando…" : "Entrar"}</button>
        </form>
        <p className="teacher-login-alt">
          É aluno? <Link href={ENTRY_ROUTES.learn} prefetch={false}>Entrar no {BRAND.learn}</Link>
        </p>
      </section>
      <LegalLinks className="teacher-login-legal" />
    </main>
  );
}
