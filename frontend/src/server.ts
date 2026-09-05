import "zone.js/node";

import express, {
  type NextFunction,
  type Request,
  type Response,
} from "express";

import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { request as httpRequest } from "node:http";
import { request as httpsRequest } from "node:https";

import {
  AngularNodeAppEngine,
  isMainModule,
  writeResponseToNodeResponse,
} from "@angular/ssr/node";

/**
 * -----------------------------------------------------------------------------
 * Paths
 * -----------------------------------------------------------------------------
 */

const serverDistFolder = dirname(fileURLToPath(import.meta.url));
const browserDistFolder = resolve(serverDistFolder, "../browser");

/**
 * -----------------------------------------------------------------------------
 * Application
 * -----------------------------------------------------------------------------
 */

const app = express();
const angularApp = new AngularNodeAppEngine();

/**
 * -----------------------------------------------------------------------------
 * Runtime configuration
 * -----------------------------------------------------------------------------
 */

const PORT = Number.parseInt(process.env["PORT"] ?? "4000", 10);

if (!Number.isInteger(PORT) || PORT < 1 || PORT > 65_535) {
  throw new Error(`Invalid PORT: ${process.env["PORT"] ?? "<undefined>"}`);
}

/**
 * INTERNAL_API_URL
 *
 * Used by the frontend container when Express is responsible for forwarding
 * browser API requests to the backend.
 *
 * Example:
 *   INTERNAL_API_URL=http://backend:8080
 *
 * In production behind Nginx, Nginx may own the /api/v1 routing instead.
 */
const INTERNAL_API_URL =
  process.env["INTERNAL_API_URL"] ?? "http://localhost:8080";

let apiTarget: URL;

try {
  apiTarget = new URL(INTERNAL_API_URL);
} catch {
  throw new Error(`Invalid INTERNAL_API_URL: ${INTERNAL_API_URL}`);
}

if (!["http:", "https:"].includes(apiTarget.protocol)) {
  throw new Error(
    `INTERNAL_API_URL must use http:// or https://: ${INTERNAL_API_URL}`,
  );
}

const API_PREFIX = "/api/v1";
const API_PROXY_TIMEOUT_MS = 30_000;

/**
 * -----------------------------------------------------------------------------
 * Security / caching configuration
 * -----------------------------------------------------------------------------
 *
 * Personalized/authenticated pages must never be shared through a public cache.
 */

const NO_CACHE_PATH_PREFIXES = [
  "/admin",
  "/dashboard",
  "/login",
  "/register",
  "/forgot-password",
  "/reset-password",
  "/verify-email",
  "/oauth-callback",
  "/quote",
];

/**
 * -----------------------------------------------------------------------------
 * Health / readiness endpoints
 * -----------------------------------------------------------------------------
 *
 * These endpoints intentionally do not depend on:
 * - Angular SSR
 * - PostgreSQL
 * - Redis
 * - backend availability
 *
 * They only indicate that the frontend process is alive and ready to serve.
 */

app.get("/healthz", (_req: Request, res: Response) => {
  res.status(200).json({
    status: "UP",
    service: "neelastack-frontend",
  });
});

app.get("/readyz", (_req: Request, res: Response) => {
  res.status(200).json({
    status: "UP",
    service: "neelastack-frontend",
  });
});

/**
 * -----------------------------------------------------------------------------
 * API reverse proxy
 * -----------------------------------------------------------------------------
 *
 * The browser uses relative URLs such as:
 *
 *   /api/v1/auth/login
 *
 * In the standalone Docker/E2E topology there may be no Nginx in front of the
 * frontend container, so Express forwards those requests to the backend.
 *
 * Only /api/v1 is proxied.
 */

function proxyApiRequest(
  req: Request,
  res: Response,
  next: NextFunction,
): void {
  /**
   * Extra safety check.
   *
   * Express already mounted this middleware under /api/v1, but checking the
   * original URL prevents accidental forwarding if middleware behavior changes.
   */
  if (!req.originalUrl.startsWith(API_PREFIX)) {
    next();
    return;
  }

  let target: URL;

  try {
    target = new URL(req.originalUrl, apiTarget);
  } catch (error) {
    next(error);
    return;
  }

  /**
   * Hop-by-hop headers must not be blindly forwarded between HTTP connections.
   */
  const hopByHopHeaders = new Set([
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
  ]);

  /**
   * Copy end-to-end request headers.
   */
  const headers: Record<string, string | string[] | undefined> = {};

  for (const [name, value] of Object.entries(req.headers)) {
    if (hopByHopHeaders.has(name.toLowerCase())) {
      continue;
    }

    headers[name] = value;
  }

  /**
   * Backend should receive the actual upstream target host.
   */
  headers["host"] = target.host;

  /**
   * Forwarding metadata.
   *
   * These are useful for backend logging, auditing, rate limiting, and
   * observability.
   */
  if (req.headers.host) {
    headers["x-forwarded-host"] = req.headers.host;
  }

  headers["x-forwarded-proto"] = req.protocol;

  if (req.ip) {
    headers["x-forwarded-for"] = req.ip;
  }

  /**
   * Preserve the request exactly enough for:
   * - JSON POST/PUT/PATCH
   * - form submissions
   * - file uploads
   * - Authorization headers
   * - cookies
   * - content type
   */
  const requestOptions = {
    protocol: target.protocol,
    hostname: target.hostname,
    port: target.port || undefined,
    method: req.method,
    path: `${target.pathname}${target.search}`,
    headers,
  };

  const requestFn = target.protocol === "https:" ? httpsRequest : httpRequest;

  const proxyReq = requestFn(requestOptions, (proxyRes) => {
    /**
     * Forward backend HTTP status.
     */
    res.status(proxyRes.statusCode ?? 502);

    /**
     * Forward response headers except hop-by-hop headers.
     */
    for (const [name, value] of Object.entries(proxyRes.headers)) {
      if (hopByHopHeaders.has(name.toLowerCase())) {
        continue;
      }

      if (value !== undefined) {
        res.setHeader(name, value);
      }
    }

    /**
     * If the upstream response itself fails while streaming, terminate
     * cleanly.
     */
    proxyRes.on("error", (error) => {
      if (res.headersSent) {
        res.destroy(error);
        return;
      }

      next(error);
    });

    /**
     * Stream backend response directly to the client.
     */
    proxyRes.pipe(res);
  });

  /**
   * Prevent a backend request from hanging indefinitely.
   */
  proxyReq.setTimeout(API_PROXY_TIMEOUT_MS, () => {
    proxyReq.destroy(
      new Error(
        `API proxy timeout after ${API_PROXY_TIMEOUT_MS}ms: ` +
          `${req.method} ${req.originalUrl}`,
      ),
    );
  });

  /**
   * Handle connection / DNS / socket errors.
   */
  proxyReq.on("error", (error) => {
    if (res.headersSent) {
      res.destroy(error);
      return;
    }

    next(error);
  });

  /**
   * Stream the incoming request directly upstream.
   *
   * We intentionally do not call express.json() / express.urlencoded()
   * before the proxy because doing so would consume the request stream and
   * could break POST bodies or multipart uploads.
   */
  req.pipe(proxyReq);
}

/**
 * API middleware must come before Angular SSR.
 */
app.use(API_PREFIX, proxyApiRequest);

/**
 * -----------------------------------------------------------------------------
 * Static assets
 * -----------------------------------------------------------------------------
 */

app.use(
  express.static(browserDistFolder, {
    maxAge: "1y",
    index: false,

    /**
     * Do not expose hidden files such as .env-style files accidentally copied
     * into the browser output.
     */
    dotfiles: "deny",
  }),
);

/**
 * -----------------------------------------------------------------------------
 * Angular SSR
 * -----------------------------------------------------------------------------
 *
 * All non-static, non-API routes are handled by Angular SSR.
 */

app.use("*", (req: Request, res: Response, next: NextFunction) => {
  /**
   * Absolute safety net:
   *
   * No /api/* request should ever reach Angular SSR.
   */
  if (req.originalUrl.startsWith("/api/")) {
    res.status(404).json({
      error: "API route not found",
    });
    return;
  }

  /**
   * Personalized routes must never use public caching.
   */
  const isNoCachePath = NO_CACHE_PATH_PREFIXES.some((prefix) =>
    req.path.startsWith(prefix),
  );

  if (isNoCachePath) {
    res.setHeader("Cache-Control", "no-store");
  } else {
    /**
     * Public content:
     * - fresh for 60 seconds
     * - may be served stale for up to 1 hour while a new response is
     *   revalidated by the edge/cache layer
     */
    res.setHeader(
      "Cache-Control",
      "public, max-age=60, stale-while-revalidate=3600",
    );
  }

  angularApp
    .handle(req)
    .then((response) => {
      if (response) {
        return writeResponseToNodeResponse(response, res);
      }

      next();
      return undefined;
    })
    .catch(next);
});

/**
 * -----------------------------------------------------------------------------
 * Central error handler
 * -----------------------------------------------------------------------------
 */

app.use((error: unknown, _req: Request, res: Response, _next: NextFunction) => {
  console.error("[SSR] Request failed:", error);

  /**
   * Headers may already have been sent when an upstream stream fails.
   */
  if (res.headersSent) {
    return;
  }

  res.status(500).json({
    error: "Internal server error",
  });
});

/**
 * -----------------------------------------------------------------------------
 * Server lifecycle
 * -----------------------------------------------------------------------------
 *
 * IMPORTANT:
 * app.listen() is intentionally inside isMainModule().
 *
 * This allows Angular's SSR machinery to import this module without
 * accidentally opening another HTTP listener.
 */

if (isMainModule(import.meta.url)) {
  const server = app.listen(PORT, () => {
    console.log(`[SSR] Neelastack frontend listening on port ${PORT}`);

    console.log(`[SSR] Internal API target: ${apiTarget.origin}`);
  });

  /**
   * Keep-alive tuning.
   *
   * headersTimeout should be slightly larger than keepAliveTimeout.
   */
  server.keepAliveTimeout = 65_000;
  server.headersTimeout = 70_000;

  /**
   * Graceful shutdown.
   *
   * Docker sends SIGTERM during container replacement/restart.
   */
  const shutdown = (signal: string): void => {
    console.log(`[SSR] Received ${signal}; shutting down gracefully...`);

    server.close((error) => {
      if (error) {
        console.error("[SSR] Graceful shutdown failed:", error);

        process.exitCode = 1;
        return;
      }

      console.log("[SSR] HTTP server closed.");
    });

    /**
     * Hard upper bound so a stuck connection cannot keep the container alive
     * forever during deployment.
     */
    setTimeout(() => {
      console.error("[SSR] Forced shutdown after grace period.");

      process.exit(1);
    }, 10_000).unref();
  };

  process.once("SIGTERM", () => shutdown("SIGTERM"));
  process.once("SIGINT", () => shutdown("SIGINT"));
}

/**
 * Export Express application for Angular SSR.
 */
export default app;
