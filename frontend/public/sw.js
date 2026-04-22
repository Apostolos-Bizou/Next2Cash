// Kill-switch service worker.
// If a user previously had a PWA installed, this SW takes over,
// clears all caches, unregisters itself, and reloads the page.
// After one visit, no SW remains on the client.
self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    try {
      const cacheKeys = await caches.keys();
      await Promise.all(cacheKeys.map((key) => caches.delete(key)));
    } catch (e) { /* ignore */ }

    try {
      await self.registration.unregister();
    } catch (e) { /* ignore */ }

    try {
      const clientList = await self.clients.matchAll({ type: 'window' });
      clientList.forEach((client) => {
        if (client.url && 'navigate' in client) {
          client.navigate(client.url);
        }
      });
    } catch (e) { /* ignore */ }
  })());
});