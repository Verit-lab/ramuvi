import "@/styles/app-ui.css";
import type { ReactNode } from "react";

/** Load the full educational UI only when entering this app environment. */
export default function EnvironmentLayout({ children }: { children: ReactNode }) {
  return children;
}
