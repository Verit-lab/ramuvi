"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import LibraryMaterialView from "@/components/library/LibraryMaterialView";
import { LibraryAccessError, fetchMyLibraryItem, type LibraryItemContent } from "@/lib/student-library-api";
import styles from "@/components/study/study.module.css";

/** Reads the FROZEN version the account saved; later editorial edits never change it. */
export default function MyLibraryItemPage({ itemId }: { itemId: string }) {
  const [item, setItem] = useState<LibraryItemContent | null>(null);
  const [error, setError] = useState<LibraryAccessError | null>(null);

  useEffect(() => {
    let active = true;
    void fetchMyLibraryItem(itemId)
      .then((loaded) => { if (active) setItem(loaded); })
      .catch((caught) => { if (active) setError(caught instanceof LibraryAccessError ? caught : new LibraryAccessError("Não foi possível abrir o material.", "NETWORK", 0)); });
    return () => { active = false; };
  }, [itemId]);

  return <div className={styles.readerView}>
    <nav className={styles.readerTopbar} aria-label="Navegação do material"><Link href="/learn/library">← Minha Biblioteca</Link></nav>
    {error && <div className={styles.error} role="alert">
      <strong>{error.kind === "ACCOUNT_REQUIRED" ? "Entre com sua conta de estudante." : "Material não encontrado na sua Biblioteca."}</strong>
      <p>{error.message}</p>
    </div>}
    {!error && !item && <div className={styles.empty} role="status">Abrindo material…</div>}
    {item && <LibraryMaterialView material={item} />}
  </div>;
}
