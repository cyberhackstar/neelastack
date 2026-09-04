import { provideServerRendering, withRoutes } from '@angular/ssr';
import { mergeApplicationConfig, ApplicationConfig } from '@angular/core';
import { provideHttpClient, withInterceptors, withFetch } from '@angular/common/http';
import { appConfig } from './app.config';
import { ssrApiInterceptor } from './core/interceptors/ssr-api.interceptor';
import { serverRoutes } from './app.routes.server';

const serverConfig: ApplicationConfig = {
  providers: [provideServerRendering(withRoutes(serverRoutes)), provideHttpClient(withFetch(), withInterceptors([ssrApiInterceptor]))],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
