import type { Metadata } from "next";
import "@/styles/app-ui.css";
import type { ReactNode } from "react";
import LearnLayoutShell from "@/components/study/LearnLayoutShell";
import { BRAND } from "@/lib/brand";

export const metadata: Metadata = {
  title: { absolute: BRAND.learn, template: `%s · ${BRAND.learn}` },
  description: "Ambiente do aluno: jornadas, atividades, progresso, conquistas e aulas ao vivo.",
  robots: { index: false, follow: false },
};

export default function LearnLayout({ children }: { children: ReactNode }) {
  return <LearnLayoutShell>{children}</LearnLayoutShell>;
}
