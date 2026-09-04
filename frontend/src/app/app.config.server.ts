import { mergeApplicationConfig, ApplicationConfig } from '@angular/core';
import { provideServerRendering } from '@angular/platform-server';
import { provideServerRouting } from '@angular/ssr';
import { provideHttpClient, withInterceptors, withFetch } from '@angular/common/http';
import { appConfig } from './app.config';
import { ssrApiInterceptor } from './core/interceptors/ssr-api.interceptor';
import { serverRoutes } from './app.routes.server';

const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(),
    provideServerRouting(serverRoutes),
    // Re-provides HttpClient for the server with the SSR-only interceptor
    // added. This intentionally shadows the browser HttpClient providers
    // from appConfig — Angular's DI uses the last-registered provider, so
    // this one wins during server rendering, and the extra interceptor
    // never ships in the browser bundle at all.
    provideHttpClient(withFetch(), withInterceptors([ssrApiInterceptor])),
  ],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
