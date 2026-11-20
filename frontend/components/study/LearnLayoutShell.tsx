"use client";

import type { ReactNode } from "react";
import { usePathname } from "next/navigation";
import StudyShell from "@/components/study/StudyShell";
import styles from "@/components/study/study.module.css";

/**
 * /learn renders its own entry (account access or dashboard); sub-routes share the study shell.
 * A shared Library link is public: a visitor without an account gets the material, not the
 * student navigation (ADR-0076 D8: no academic area for anonymous viewers).
 */
export default function LearnLayoutShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  if (pathname === "/learn") return <>{children}</>;
  if (pathname === "/learn/library/shared") return <div className={`${styles.shell} ${styles.publicShell}`}>{children}</div>;
  return <StudyShell>{children}</StudyShell>;
}
