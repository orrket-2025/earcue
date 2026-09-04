const CACHE = 'earcue-v14';
const SHELL = ['./', './index.html', './manifest.json', './icon.svg', './icon-maskable.svg'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// Same-origin app files: network first so updates arrive, cache as the offline fallback.
// Fonts and libraries: cache first, filled on the first successful fetch.
self.addEventListener('fetch', (e) => {
  if (e.request.method !== 'GET') return;
  const url = new URL(e.request.url);
  const sameOrigin = url.origin === location.origin;
  const cacheable = sameOrigin || url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com' || url.hostname === 'cdnjs.cloudflare.com';
  if (!cacheable) return;

  const fromNetwork = () => fetch(e.request).then((res) => {
    if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
    return res;
  });

  if (sameOrigin) {
    e.respondWith(fromNetwork().catch(() => caches.match(e.request).then((hit) => hit || caches.match('./index.html'))));
  } else {
    e.respondWith(caches.match(e.request).then((hit) => hit || fromNetwork()));
  }
});
