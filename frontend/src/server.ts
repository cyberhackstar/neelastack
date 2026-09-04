import 'zone.js/node';
import express from 'express';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';
import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';

const serverDistFolder = dirname(fileURLToPath(import.meta.url));
const browserDistFolder = resolve(serverDistFolder, '../browser');

const app = express();
const angularApp = new AngularNodeAppEngine();

app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
  }),
);

// ISR-equivalent for a DB-backed SSR app: literal build-time prerendering doesn't fit here
// (content — blog posts, portfolio case studies — lives in Postgres and changes outside a
// build, so a stale prerendered file could silently outlive an edit). Instead, cache the
// rendered HTML at the edge/CDN/browser for a short window with stale-while-revalidate: a
// visitor (or Googlebot) gets an instantly-served cached response, the origin gets a fresh
// render kicked off in the background, and the cache is topped up for the next request —
// the same "fast now, fresh soon" trade-off ISR makes, without a static file that can drift
// from the database. Admin/auth/API-adjacent routes explicitly opt out below since serving
// a cached, personalized, or non-indexable page from a shared cache would be a real bug.
const NO_CACHE_PATH_PREFIXES = ['/admin', '/dashboard', '/login', '/register',
  '/forgot-password', '/reset-password', '/verify-email', '/oauth-callback', '/quote'];

app.use('/**', (req, res, next) => {
  const isNoCachePath = NO_CACHE_PATH_PREFIXES.some((p) => req.path.startsWith(p));
  if (isNoCachePath) {
    res.setHeader('Cache-Control', 'no-store');
  } else {
    // 60s fresh, then up to 1 hour of serving stale while a fresh copy renders in the
    // background — tune per traffic; content pages change infrequently enough that this
    // is a large crawl/latency win with negligible staleness risk.
    res.setHeader('Cache-Control', 'public, max-age=60, stale-while-revalidate=3600');
  }

  angularApp
    .handle(req)
    .then((response) =>
      response ? writeResponseToNodeResponse(response, res) : next(),
    )
    .catch(next);
});

if (isMainModule(import.meta.url)) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, () => {
    console.log(`Neelastack SSR server listening on http://localhost:${port}`);
  });
}

export default app;
