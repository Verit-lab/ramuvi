"use client";

import type { ReactNode } from "react";
import { usePathname } from "next/navigation";
import StudyShell from "@/components/study/StudyShell";

/** /learn renders its own entry (account access or dashboard); sub-routes share the study shell. */
export default function LearnLayoutShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return pathname === "/learn" ? <>{children}</> : <StudyShell>{children}</StudyShell>;
}
