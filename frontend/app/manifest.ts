import type { MetadataRoute } from "next";
import { BRAND } from "@/lib/brand";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: BRAND.name,
    short_name: BRAND.name,
    description: "Jornadas de aprendizagem, atividades interativas e participação ao vivo em aulas.",
    start_url: "/join",
    scope: "/",
    display: "standalone",
    orientation: "portrait-primary",
    background_color: "#08100e",
    theme_color: "#08100e",
    lang: "pt-BR",
    icons: [
      { src: "/icons/ramuvi-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icons/ramuvi-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icons/ramuvi-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
