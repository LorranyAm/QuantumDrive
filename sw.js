/* Service worker do Quantum Drive: deixa o app abrir mesmo sem internet.
   Página: tenta a rede primeiro (sempre a versão mais nova) e usa a cópia salva se estiver offline.
   EmailJS e VLibras sempre vão direto para a internet. */
const CACHE = 'quantumdrive-v3';
const ARQUIVOS = ['./', './index.html', './manifest.webmanifest', './icones/icon-192.png', './icones/icon-512.png', './icones/apple-touch-icon.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(ARQUIVOS)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
  if (req.mode === 'navigate') {
    e.respondWith(fetch(req).then(r => { const c = r.clone(); caches.open(CACHE).then(k => k.put('./index.html', c)); return r; })
      .catch(() => caches.match('./index.html')));
    return;
  }
  e.respondWith(caches.match(req).then(salvo => {
    const rede = fetch(req).then(r => { if (r.ok) { const c = r.clone(); caches.open(CACHE).then(k => k.put(req, c)); } return r; }).catch(() => salvo);
    return salvo || rede;
  }));
});
