"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import JourneyContentReader from "@/components/study/JourneyContentReader";
import { completeStudyContent, fetchStudyContent, fetchStudyJourney, fetchTeacherContentPreview, fetchTeacherStudyPreview, startStudyContent, studyDeviceClaims, type JourneyContentDetailView, type JourneyStudyView } from "@/lib/study-api";
import { completeVerifiedContent, fetchVerifiedContent, fetchVerifiedJourney, startVerifiedContent, verifiedStudyAccess } from "@/lib/verified-study-api";
import styles from "@/components/study/study.module.css";
import { isExcerptOfBody, studyExcerpt } from "@/lib/study-excerpt";

type ReaderData = { mode: "TEACHER" | "DEVICE" | "ACCOUNT"; token: string; journey: JourneyStudyView; content: JourneyContentDetailView };

export default function ContentReaderPage() {
  const { journeyId, contentId } = useParams<{ journeyId: string; contentId: string }>();
  const router = useRouter();
  const searchParams = useSearchParams();
  const teacherPreview = searchParams.get("preview") === "teacher";
  const [data, setData] = useState<ReaderData | null>(null);
  const [error, setError] = useState("");
  const [completing, setCompleting] = useState(false);
  useEffect(() => { let active = true; (async () => {
    if (teacherPreview) {
      try {
        const [journey, content] = await Promise.all([fetchTeacherStudyPreview(journeyId), fetchTeacherContentPreview(journeyId, contentId)]);
        if (active) setData({ mode: "TEACHER", token: "", journey, content });
      } catch { if (active) setError("Não foi possível abrir a prévia deste conteúdo."); }
      return;
    }
    try {
      const access = await verifiedStudyAccess();
      if (access.mode === "ACCOUNT") {
        const [journey, content] = await Promise.all([
          fetchVerifiedJourney(journeyId),
          fetchVerifiedContent(journeyId, contentId),
        ]);
        if (content.state === "CURRENT") await startVerifiedContent(journeyId, contentId);
        if (active) setData({ mode: "ACCOUNT", token: "", journey, content });
        return;
      }
    } catch (caught) {
      // Never retry with a device claim after a verified account is denied.
      if (active) setError(caught instanceof Error ? caught.message : "Não foi possível validar sua conta.");
      return;
    }
    for (const claim of studyDeviceClaims()) {
      try {
        const [journey, content] = await Promise.all([fetchStudyJourney(journeyId, claim.token), fetchStudyContent(journeyId, contentId, claim.token)]);
        if (content.state === "CURRENT") await startStudyContent(journeyId, contentId, claim.token);
        if (active) setData({ mode: "DEVICE", token: claim.token, journey, content });
        return;
      } catch {}
    }
    if (active) setError("Não foi possível abrir este conteúdo. Ele pode estar bloqueado ou sua identificação de estudo pode ter expirado.");
  })(); return () => { active = false; }; }, [journeyId, contentId, teacherPreview]);

  const navigation = useMemo(() => {
    if (!data) return { previous: null, next: null, position: 0, total: 0, moduleTitle: "Jornada" };
    const flat = data.journey.modules.flatMap(module => module.contents.map(content => ({ ...content, moduleTitle: module.title })));
    const index = flat.findIndex(content => content.id === contentId);
    return { previous: index > 0 ? flat[index - 1] : null, next: index >= 0 && index < flat.length - 1 ? flat[index + 1] : null, position: index + 1, total: flat.length, moduleTitle: flat[index]?.moduleTitle || "Jornada" };
  }, [data, contentId]);

  // Left/right arrows walk the Journey like the footer links do. The right arrow
  // only follows a link that is already there; it never concludes a content,
  // because completion records progress and must stay a deliberate click.
  const previousHref = navigation.previous ? `/learn/journeys/${journeyId}/content/${navigation.previous.id}${teacherPreview ? "?preview=teacher" : ""}` : null;
  const nextHref = navigation.next && (teacherPreview || (data?.content.state === "COMPLETED" && navigation.next.state !== "LOCKED"))
    ? `/learn/journeys/${journeyId}/content/${navigation.next.id}${teacherPreview ? "?preview=teacher" : ""}`
    : null;
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
      if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey || event.isComposing) return;
      if (arrowKeysBelongToTarget(event.target)) return;
      const href = event.key === "ArrowLeft" ? previousHref : nextHref;
      if (!href) return;
      event.preventDefault();
      router.push(href);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [previousHref, nextHref, router]);

  async function conclude() {
    if (!data || completing) return;
    setCompleting(true);
    try {
      const updated = data.mode === "ACCOUNT"
        ? await completeVerifiedContent(journeyId, contentId)
        : await completeStudyContent(journeyId, contentId, data.token);
      updated.newlyUnlockedAchievements.forEach((achievement) => window.dispatchEvent(new CustomEvent("arena:achievement-unlocked", { detail: { title: achievement.title, description: achievement.description } })));
      const flat = updated.modules.flatMap(module => module.contents);
      const next = flat.find(content => content.state === "CURRENT");
      router.push(next ? `/learn/journeys/${journeyId}/content/${next.id}` : `/learn/journeys/${journeyId}`);
    } catch (caught) { setError(caught instanceof Error ? caught.message : "Não foi possível concluir o conteúdo."); setCompleting(false); }
  }

  if (error && !data) return <div className={styles.error}>{error}<div className={styles.readerErrorAction}><Link href={`/learn/journeys/${journeyId}`}>Voltar à jornada</Link></div></div>;
  if (!data) return <div className={styles.empty}>Carregando conteúdo…</div>;

  return <div className={styles.readerView}>
    {teacherPreview && <div className={styles.teacherPreviewBar}><span><strong>Prévia do professor</strong> · leitura sem registrar progresso</span><a href={`/teach?journey=${encodeURIComponent(journeyId)}`}>Editar jornada</a></div>}
    <header className={styles.readerTopbar} aria-label="Navegação do conteúdo">
      <div className={styles.readerTopbarRow}>
        <Link className={styles.readerBackLink}
          href={"/learn/journeys/" + journeyId + (teacherPreview ? "?preview=teacher" : "")}
          aria-label={"Voltar à Jornada — " + navigation.moduleTitle}>
          <span className={styles.readerBackArrow} aria-hidden="true">←</span>
          <span>Voltar à Jornada</span>
        </Link>
        <span className={styles.readerPositionBadge}
          aria-label={"Conteúdo " + navigation.position + " de " + navigation.total + " na Jornada"}
          title={"Posição na Jornada: " + navigation.position + " de " + navigation.total}>
          {navigation.position} de {navigation.total}
        </span>
      </div>
      <div className={styles.readerPositionTrack} aria-hidden="true">
        <span style={{ width: (navigation.total ? Math.min(100, navigation.position / navigation.total * 100) : 0) + "%" }} />
      </div>
    </header>
    <div className={styles.readerScaled}>
      <div className={styles.readerHeading}><h1>{data.content.title}</h1>{studyExcerpt(data.content.description, 220) && !isExcerptOfBody(data.content.description, data.content.body) && <p>{studyExcerpt(data.content.description, 220)}</p>}</div>
      <JourneyContentReader content={data.content} />
    </div>
    {error && <div className={styles.error}>{error}</div>}
    <footer className={styles.readerFooter} aria-label="Navegação entre conteúdos">
      <div className={styles.readerFooterPrevious}>
        {previousHref && <Link className={styles.readerPreviousLink} href={previousHref}
          title={navigation.previous!.title} aria-label={"Conteúdo anterior: " + navigation.previous!.title}
          aria-keyshortcuts="ArrowLeft">
          <span className={styles.readerPreviousArrow} aria-hidden="true">←</span>
          <span className={styles.readerPreviousText}>
            <span className={styles.readerPreviousMeta}>Conteúdo anterior</span>
            <span className={styles.readerPreviousTitle}>{navigation.previous!.title}</span>
          </span>
        </Link>}
      </div>
      <div className={styles.readerFooterAction}>
        {teacherPreview
          ? (navigation.next
            ? <Link className={styles.readerPrimary} href={"/learn/journeys/" + journeyId + "/content/" + navigation.next.id + "?preview=teacher"} aria-keyshortcuts="ArrowRight">Próximo conteúdo <span aria-hidden="true">→</span></Link>
            : <Link className={styles.readerPrimary} href={"/learn/journeys/" + journeyId + "?preview=teacher"}>Voltar à Jornada</Link>)
          : data.content.state === "COMPLETED"
            ? (navigation.next && navigation.next.state !== "LOCKED"
              ? <Link className={styles.readerPrimary} href={"/learn/journeys/" + journeyId + "/content/" + navigation.next.id} aria-keyshortcuts="ArrowRight">Próximo conteúdo <span aria-hidden="true">→</span></Link>
              : <Link className={styles.readerPrimary} href={"/learn/journeys/" + journeyId}>Voltar à Jornada</Link>)
            : <button type="button" className={styles.readerPrimary} disabled={completing} onClick={conclude}>
              {completing ? "Concluindo…" : navigation.next && navigation.next.state !== "LOCKED" ? "Concluir e continuar" : "Concluir conteúdo"}
              {!completing && navigation.next && navigation.next.state !== "LOCKED" && <span aria-hidden="true">→</span>}
            </button>}
      </div>
    </footer>
  </div>;
}

// Arrows keep their own meaning in form fields, editable text, and anything
// that scrolls sideways (wide code blocks and tables).
function arrowKeysBelongToTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) return false;
  if (target.isContentEditable || target.closest("input, textarea, select, [role='slider'], [role='tablist']")) return true;
  for (let element: HTMLElement | null = target; element && element !== document.body; element = element.parentElement) {
    const overflowX = window.getComputedStyle(element).overflowX;
    if ((overflowX === "auto" || overflowX === "scroll") && element.scrollWidth > element.clientWidth) return true;
  }
  return false;
}
