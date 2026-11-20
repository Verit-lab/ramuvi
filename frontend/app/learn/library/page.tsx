import type { Metadata } from "next";
import MyLibraryPage from "@/components/library/MyLibraryPage";

export const metadata: Metadata = { title: "Minha Biblioteca", robots: { index: false, follow: false } };

export default function LearnLibraryPage() {
  return <MyLibraryPage />;
}
