"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { fetchStudentCapabilities } from "@/lib/student-capabilities-api";

/** Shown only when the backend exposes Minha Biblioteca for this verified account. */
export default function PersonalLibraryLink({ className, children = "Minha Biblioteca" }: { className?: string; children?: React.ReactNode }) {
  const [enabled, setEnabled] = useState(false);
  useEffect(() => {
    let active = true;
    void fetchStudentCapabilities().then((capabilities) => { if (active) setEnabled(capabilities.personalLibrary); });
    return () => { active = false; };
  }, []);
  if (!enabled) return null;
  return <Link href="/learn/library" className={className} data-testid="personal-library-link">{children}</Link>;
}
