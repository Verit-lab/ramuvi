import type { Metadata } from "next";
import MyLibraryItemPage from "@/components/library/MyLibraryItemPage";

export const metadata: Metadata = { title: "Material salvo", robots: { index: false, follow: false } };

export default async function LearnLibraryItemPage({ params }: { params: Promise<{ itemId: string }> }) {
  const { itemId } = await params;
  return <MyLibraryItemPage itemId={itemId} />;
}
