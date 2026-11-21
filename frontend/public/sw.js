/* PWA shell only. The backend, student tokens and interaction state are NEVER cached. */
// v0.12: Ramuvi identity; a new version and prefix invalidate the cached
// pre-rebranding icons and offline page.
const VERSION = "v0.12.2-library-1";
const CACHE_PREFIX = "ramuvi-";
// Caches created by releases before the rebranding. Still owned by this app,
// so they are removed on activation instead of being orphaned.
const LEGACY_CACHE_PREFIXES = ["arena-dev-"];
const SHELL_CACHE = `${CACHE_PREFIX}shell-${VERSION}`;
const STATIC_CACHE = `${CACHE_PREFIX}static-${VERSION}`;
const OFFLINE_PAGE = "/offline.html";
const OFFLINE_READER = "/offline-journeys.html";
const OFFLINE_READER_ASSETS = ["/offline-journeys.js", "/offline-journeys.css"];
const SHELL_URLS = [
  OFFLINE_PAGE,
  OFFLINE_READER,
  ...OFFLINE_READER_ASSETS,
  "/manifest.webmanifest",
  "/icons/ramuvi-192.png",
  "/icons/ramuvi-512.png",
  "/icons/ramuvi-maskable-512.png",
  "/icons/ramuvi.svg",
];

self.addEventListener("install", (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(SHELL_CACHE);
    // Do not take over an old tab unless the neutral offline fallback was cached.
    const offlineResponse = await fetch(OFFLINE_PAGE, { cache: "reload" });
    if (!offlineResponse.ok) throw new Error("Offline shell unavailable");
    await cache.put(OFFLINE_PAGE, offlineResponse);
    // The opt-in academic reader must be complete before this SW takes control.
    await Promise.all([OFFLINE_READER, ...OFFLINE_READER_ASSETS].map(async (url) => {
      const response = await fetch(url, { cache: "reload" });
      if (!response.ok) throw new Error("Offline reader unavailable");
      await cache.put(url, response);
    }));
    await Promise.allSettled(SHELL_URLS.filter((url) => url !== OFFLINE_PAGE
      && url !== OFFLINE_READER && !OFFLINE_READER_ASSETS.includes(url)).map(async (url) => {
      const response = await fetch(url, { cache: "reload" });
      if (response.ok) await cache.put(url, response);
    }));
    await self.skipWaiting();
  })());
});

self.addEventListener("activate", (event) => {
  event.waitUntil((async () => {
    const keep = new Set([SHELL_CACHE, STATIC_CACHE]);
    const names = await caches.keys();
    // Never delete caches owned by another app on the same origin.
    const owned = (name) => name.startsWith(CACHE_PREFIX) || LEGACY_CACHE_PREFIXES.some((prefix) => name.startsWith(prefix));
    await Promise.all(names.filter((name) => owned(name) && !keep.has(name))
      .map((name) => caches.delete(name)));
    await self.clients.claim();
  })());
});

self.addEventListener("message", (event) => {
  if (event.data?.type === "ARENA_PWA_GET_VERSION") {
    event.source?.postMessage({ type: "ARENA_PWA_VERSION", version: VERSION });
  }
});

function isDomainRequest(url) {
  return url.pathname.startsWith("/api/") || url.pathname.startsWith("/ws/");
}

function isStaticAsset(url) {
  return url.pathname.startsWith("/_next/static/")
    || url.pathname.startsWith("/icons/")
    || url.pathname === "/manifest.webmanifest";
}

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  // No caching of API, authentication, learner responses, presence, WS or score ledger.
  if (isDomainRequest(url)) return;

  if (request.mode === "navigate") {
    event.respondWith((async () => {
      try {
        // Always fetch HTML, never store /join or SSR payloads across releases.
        return await fetch(request);
      } catch {
        const cache = await caches.open(SHELL_CACHE);
        if (url.pathname === OFFLINE_READER) {
          return (await cache.match(OFFLINE_READER)) || Response.error();
        }
        return (await cache.match(OFFLINE_PAGE)) || Response.error();
      }
    })());
    return;
  }

  if (OFFLINE_READER_ASSETS.includes(url.pathname)) {
    event.respondWith((async () => {
      const cache = await caches.open(SHELL_CACHE);
      return (await cache.match(url.pathname)) || fetch(request);
    })());
    return;
  }

  if (isStaticAsset(url)) {
    event.respondWith((async () => {
      const cache = await caches.open(STATIC_CACHE);
      const cached = await cache.match(request);
      if (cached) return cached;
      const response = await fetch(request);
      if (response.ok) await cache.put(request, response.clone());
      return response;
    })());
  }
});
