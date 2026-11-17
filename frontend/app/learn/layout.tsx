"use client";

import type { ReactNode } from "react";
import { usePathname } from "next/navigation";
import StudyShell from "@/components/study/StudyShell";

export default function LearnLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return pathname === "/learn" ? <>{children}</> : <StudyShell>{children}</StudyShell>;
}
