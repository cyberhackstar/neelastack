export const environment = {
  production: true,
  // Same-origin, relative path — matches the Nginx config in infra/nginx/neelastack.conf,
  // which proxies /api/ on this same domain to the backend container. A separate
  // api.neelastack.com subdomain was referenced here before but never actually
  // configured anywhere (no DNS, no Nginx server block for it) — that mismatch
  // would have made every API call fail in production.
  apiBaseUrl: '/api/v1',
  // Set your GA4 measurement ID (e.g. 'G-XXXXXXXXXX') before building for production.
  // Left empty by default so nothing is tracked until you deliberately turn it on.
  gaMeasurementId: '',
};
