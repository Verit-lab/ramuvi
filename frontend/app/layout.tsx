import type { Metadata, Viewport } from "next";
import PwaRuntime from "@/components/PwaRuntime";
import { BRAND } from "@/lib/brand";
// Public routes load only tokens and the accessibility foundation.
import "../styles/public-base.css";

export const metadata: Metadata = {
  // Routes set their own title; the template keeps "· Ramuvi" on all of them.
  title: { default: BRAND.name, template: `%s · ${BRAND.name}` },
  description: BRAND.description,
  applicationName: BRAND.name,
  manifest: "/manifest.webmanifest",
  // v0.12: icons repeat the in-app Ramuvi mark (accent square, dark "R").
  icons: {
    icon: [
      { url: "/icons/ramuvi.svg", type: "image/svg+xml" },
      { url: "/icons/ramuvi-32.png", sizes: "32x32", type: "image/png" },
      { url: "/icons/ramuvi-192.png", sizes: "192x192", type: "image/png" },
      { url: "/icons/ramuvi-512.png", sizes: "512x512", type: "image/png" },
    ],
    apple: { url: "/icons/ramuvi-apple-180.png", sizes: "180x180" },
  },
  appleWebApp: {
    capable: true,
    title: BRAND.name,
    statusBarStyle: "black-translucent",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: dark)", color: "#08100e" },
    { media: "(prefers-color-scheme: light)", color: "#e9eeeb" },
  ],
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="pt-BR" suppressHydrationWarning>
      <body>
        <script
          dangerouslySetInnerHTML={{
            __html: `(function(){try{var p=localStorage.getItem('arena-dev-theme')||'system';var l=window.matchMedia('(prefers-color-scheme: light)').matches;var r=p==='system'?(l?'light':'dark'):p;document.documentElement.dataset.theme=r;document.documentElement.dataset.themePreference=p;document.documentElement.style.colorScheme=r;}catch(e){document.documentElement.dataset.theme='dark';}})();`,
          }}
        />
        <PwaRuntime />
        {children}
      </body>
    </html>
  );
}
