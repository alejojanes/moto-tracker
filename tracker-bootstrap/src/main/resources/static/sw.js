// Service worker mínimo: permite instalar la app en el celular. No guarda datos en caché a propósito,
// para que la ubicación mostrada siempre venga del servidor.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));
self.addEventListener('fetch', () => {});
