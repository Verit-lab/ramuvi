import type { Metadata } from "next";
import SharedMaterialPage from "@/components/library/SharedMaterialPage";

export const metadata: Metadata = {
  title: "Material compartilhado",
  robots: { index: false, follow: false, nocache: true },
  referrer: "no-referrer",
};

export default function LibrarySharedPage() {
  return <SharedMaterialPage />;
}
