import { HttpInterceptorFn } from '@angular/common/http';

/**
 * SSR-only. A relative API URL like '/api/v1/...' resolves fine in the
 * browser (against the page's own origin), but on the server there's no
 * document/location to resolve it against — Angular's HttpClient would throw
 * "URL is not absolute" for a relative URL during server-side rendering.
 *
 * Rather than hardcoding a public domain into the SSR request (which would
 * mean the frontend container round-tripping out through Nginx and back in
 * just to reach a container sitting right next to it), this rewrites
 * relative /api/ calls to hit the backend directly over the internal Docker
 * network. INTERNAL_API_URL is read from the environment at container
 * startup — see docker-compose.prod.yml — defaulting to the service name
 * used in this project's own compose files.
 */
export const ssrApiInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.startsWith('/api/')) {
    const internalBase = process.env['INTERNAL_API_URL'] || 'http://backend:8080';
    return next(req.clone({ url: `${internalBase}${req.url}` }));
  }
  return next(req);
};
